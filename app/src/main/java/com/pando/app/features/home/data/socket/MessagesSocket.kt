package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.core.network.socket.PendingSocketMessage
import com.pando.app.core.network.socket.PendingSocketMessageQueue
import com.pando.app.core.session.UserSession
import com.pando.app.features.home.data.model.request.SendImageRequest
import com.pando.app.features.home.data.model.request.SendMessageRequest
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.StompClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagesSocket @Inject constructor(
    private val connectionManager: SocketConnectionManager,
    private val gson: Gson,
    private val userSession: UserSession
) {
    companion object {
        private const val TAG = "MessageSocket"
    }

    private val conversationSubscriptions = ConcurrentHashMap<UUID, ActiveSubscription>()
    private val desiredConversationIds = ConcurrentHashMap.newKeySet<UUID>()
    private val subscriptionRetryJobs = ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()

    // Chat events must not use DROP_OLDEST: losing a message is worse than
    // briefly buffering it while RecyclerView catches up.
    private val messageChannel = Channel<ChatMessageResponse>(capacity = 256)
    val message = messageChannel.receiveAsFlow()

    private val pendingMessages = PendingSocketMessageQueue()
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Any()
    private var isFlushing = false

    init {
        queueScope.launch {
            connectionManager.connectionState.collectLatest { state ->
                if (state == SocketConnectionState.Connected) {
                    desiredConversationIds.toList().forEach(::subscribeConversation)
                    requestFlush()
                } else {
                    clearActiveSubscriptions()
                }
            }
        }
    }

    @Synchronized
    fun subscribeConversation(conversationId: UUID) {
        desiredConversationIds.add(conversationId)

        val client = connectionManager.getConnectedClient() ?: run {
            return
        }

        val existing = conversationSubscriptions[conversationId]

        if (existing?.client === client && !existing.disposable.isDisposed) {
            Log.d(TAG, "Conversation đã subscribe trên client hiện tại")
            return
        }

        existing?.disposable?.dispose()
        conversationSubscriptions.remove(conversationId)

        val destination = "${SocketConstants.Chat.TOPIC_CONVERSATION}/$conversationId"
        Log.d(TAG, "Subscribe destination: $destination")

        val disposable = client
            .topic(destination)
            .subscribe(
                { topicMessage ->
                    val message = runCatching {
                        gson.fromJson(topicMessage.payload, ChatMessageResponse::class.java)
                    }.getOrElse { throwable ->
                        Log.e(TAG, "Không parse được conversation", throwable)
                        null
                    }

                    message?.let {
                        if (!messageChannel.trySend(it).isSuccess) {
                            Log.e(TAG, "Bộ đệm message đã đầy")
                        }
                    }
                },
                { throwable ->
                    Log.e(TAG, "Lỗi subscribe conversation $conversationId", throwable)
                    handleSubscriptionFailure(conversationId, client)
                }
            )

        conversationSubscriptions[conversationId] = ActiveSubscription(
                client = client,
                disposable = disposable
            )
    }

    @Synchronized
    fun unsubscribeConversation(conversationId: UUID) {
        desiredConversationIds.remove(conversationId)
        subscriptionRetryJobs.remove(conversationId)?.cancel()
        conversationSubscriptions.remove(conversationId)?.disposable?.dispose()

        Log.d(TAG, "Đã unsubscribe conversation $conversationId")
    }

    @Synchronized
    fun unsubscribeAllConversation() {
        desiredConversationIds.clear()
        subscriptionRetryJobs.values.forEach { it.cancel() }
        subscriptionRetryJobs.clear()
        conversationSubscriptions.values.forEach {
            it.disposable.dispose()
        }
        conversationSubscriptions.clear()

        Log.d(TAG, "Đã unsubscribe tất cả conversation")
    }

    fun sendMessage(conversationId: UUID, content: String) {
        if (content.isBlank()) {
            Log.e(TAG, "Nội dung tin nhắn đang trống")
            return
        }
        val ownerUserId = userSession.getCurrentUserId() ?: run {
            Log.e(TAG, "Không thể xếp tin nhắn khi chưa có phiên đăng nhập")
            return
        }

        val request = SendMessageRequest(
            conversationId = conversationId,
            content = content.trim()
        )

        val payload = gson.toJson(request)

        enqueueMessage(
            PendingSocketMessage(
                destination = SocketConstants.Chat.SEND_TEXT_DESTINATION,
                payload = payload,
                ownerUserId = ownerUserId
            )
        )
    }

    fun sendImageMessage(conversationId: UUID, postImageUrl: String) {
        if (postImageUrl.isBlank()) {
            Log.e(TAG, "Không có link ảnh")
            return
        }
        val ownerUserId = userSession.getCurrentUserId() ?: run {
            Log.e(TAG, "Không thể xếp ảnh khi chưa có phiên đăng nhập")
            return
        }

        val request = SendImageRequest(
            conversationId = conversationId,
            imageUrl = postImageUrl
        )

        val payload = gson.toJson(request)

        enqueueMessage(
            PendingSocketMessage(
                destination = SocketConstants.Chat.SEND_IMAGE_DESTINATION,
                payload = payload,
                ownerUserId = ownerUserId
            )
        )
    }

    /** Drop all account-scoped work before disconnecting or switching user. */
    fun clearSession() {
        unsubscribeAllConversation()

        val removedCount = synchronized(flushLock) {
            pendingMessages.clear()
        }
        if (removedCount > 0) {
            Log.d(TAG, "Đã xóa $removedCount tin nhắn đang chờ của phiên cũ")
        }
    }

    private fun enqueueMessage(message: PendingSocketMessage) {
        synchronized(flushLock) {
            if (!pendingMessages.addLast(message)) {
                Log.e(TAG, "Hàng đợi message đã đầy, không thể xếp message mới")
                return
            }
        }
        requestFlush()
    }

    /**
     * Drain the queue in insertion order whenever a connected STOMP client is
     * available. The queue item is removed only immediately before an attempt
     * to send; failed attempts are put back at the front for the next
     * reconnect.
     */
    private fun requestFlush() {
        synchronized(flushLock) {
            if (isFlushing) return
            isFlushing = true
        }

        queueScope.launch {
            var shouldRetry = false
            try {
                while (true) {
                    val message = pendingMessages.pollFirst() ?: break
                    val activeUserId = userSession.getCurrentUserId()
                    if (activeUserId == null || message.ownerUserId != activeUserId) {
                        Log.w(TAG, "Bỏ tin nhắn đang chờ không thuộc phiên hiện tại")
                        continue
                    }
                    val client = connectionManager.getConnectedClient()
                    if (client == null) {
                        pendingMessages.addFirst(message)
                        shouldRetry = true
                        break
                    }

                    try {
                        // A Completable subscription used to be discarded here,
                        // allowing the next item to overtake it and losing async
                        // errors. blockingAwait is safe on this dedicated IO
                        // queue and gives us strict FIFO semantics.
                        client.send(message.destination, message.payload).blockingAwait()
                        Log.d(TAG, "Đã gửi message lên STOMP")
                    } catch (throwable: Throwable) {
                        pendingMessages.addFirst(message)
                        shouldRetry = true
                        Log.e(TAG, "Không thể gửi message, sẽ thử lại khi reconnect", throwable)
                        break
                    }
                }
            } finally {
                synchronized(flushLock) {
                    isFlushing = false
                }

                if (shouldRetry &&
                    !pendingMessages.isEmpty() &&
                    connectionManager.getConnectedClient() != null
                ) {
                    // Avoid a tight loop when the broker is connected but is
                    // temporarily refusing writes. A reconnect event will also
                    // call requestFlush, and the guard keeps them serialized.
                    queueScope.launch {
                        delay(1_000L)
                        requestFlush()
                    }
                }
            }
        }
    }

    @Synchronized
    private fun clearActiveSubscriptions() {
        conversationSubscriptions.values.forEach { it.disposable.dispose() }
        conversationSubscriptions.clear()
    }

    private fun handleSubscriptionFailure(conversationId: UUID, client: StompClient) {
        synchronized(this) {
            val active = conversationSubscriptions[conversationId]
            if (active?.client === client) {
                conversationSubscriptions.remove(conversationId)?.disposable?.dispose()
            }
        }

        if (!desiredConversationIds.contains(conversationId)) return
        if (subscriptionRetryJobs[conversationId]?.isActive == true) return

        subscriptionRetryJobs[conversationId] = queueScope.launch {
            delay(1_000L)
            subscriptionRetryJobs.remove(conversationId)
            if (desiredConversationIds.contains(conversationId) &&
                connectionManager.getConnectedClient() != null
            ) {
                subscribeConversation(conversationId)
            }
        }
    }

    private data class ActiveSubscription(
        val client: StompClient,
        val disposable: Disposable
    )
}
