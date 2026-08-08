package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.core.network.socket.PendingSocketMessage
import com.pando.app.core.network.socket.PendingSocketMessageQueue
import com.pando.app.features.home.data.model.request.SendImageRequest
import com.pando.app.features.home.data.model.request.SendMessageRequest
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.StompClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagesSocket @Inject constructor(
    private val connectionManager: SocketConnectionManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "MessageSocket"
    }

    private val conversationSubscriptions = ConcurrentHashMap<UUID, ActiveSubscription>()

    private val _message = MutableSharedFlow<ChatMessageResponse>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val message = _message.asSharedFlow()

    private val pendingMessages = PendingSocketMessageQueue()
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Any()
    private var isFlushing = false

    init {
        queueScope.launch {
            connectionManager.connectionState.collectLatest { state ->
                if (state == SocketConnectionState.Connected) {
                    flushPendingMessages()
                }
            }
        }
    }

    @Synchronized
    fun subscribeConversation(conversationId: UUID) {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
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
                        _message.tryEmit(it)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Lỗi subscribe conversation $conversationId", throwable)
                }
            )

        conversationSubscriptions[conversationId] = ActiveSubscription(
                client = client,
                disposable = disposable
            )
    }

    @Synchronized
    fun unsubscribeConversation(conversationId: UUID) {
        conversationSubscriptions.remove(conversationId)?.disposable?.dispose()

        Log.d(TAG, "Đã unsubscribe conversation $conversationId")
    }

    @Synchronized
    fun unsubscribeAllConversation() {
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

        val request = SendMessageRequest(
            conversationId = conversationId,
            content = content.trim()
        )

        val payload = gson.toJson(request)

        enqueueMessage(
            PendingSocketMessage(
                destination = SocketConstants.Chat.SEND_TEXT_DESTINATION,
                payload = payload
            )
        )
    }

    fun sendImageMessage(conversationId: UUID, postImageUrl: String) {
        if (postImageUrl.isBlank()) {
            Log.e(TAG, "Không có link ảnh")
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
                payload = payload
            )
        )
    }

    private fun enqueueMessage(message: PendingSocketMessage) {
        synchronized(flushLock) {
            pendingMessages.addLast(message)
        }
        flushPendingMessages()
    }

    /**
     * Drain the queue in insertion order whenever a connected STOMP client is
     * available. The queue item is removed only immediately before an attempt
     * to send; failed attempts are put back at the front for the next
     * reconnect.
     */
    private fun flushPendingMessages() {
        synchronized(flushLock) {
            if (isFlushing) return
            isFlushing = true
        }

        var stopAfterCurrentMessage = false

        try {
            while (true) {
                val message = pendingMessages.pollFirst() ?: return
                val client = connectionManager.getConnectedClient()
                if (client == null) {
                    pendingMessages.addFirst(message)
                    return
                }

                try {
                    var failedSynchronously = false
                    client.send(message.destination, message.payload).subscribe(
                        {
                            Log.d(TAG, "Đã gửi message lên STOMP")
                        },
                        { throwable ->
                            pendingMessages.addFirst(message)
                            failedSynchronously = true
                            Log.e(TAG, "Không thể gửi message, sẽ thử lại khi reconnect", throwable)
                        }
                    )
                    if (failedSynchronously) {
                        stopAfterCurrentMessage = true
                        return
                    }
                } catch (throwable: Throwable) {
                    pendingMessages.addFirst(message)
                    Log.e(TAG, "Không thể gửi message, sẽ thử lại khi reconnect", throwable)
                    stopAfterCurrentMessage = true
                    return
                }
            }
        } finally {
            val shouldFlushAgain: Boolean
            synchronized(flushLock) {
                isFlushing = false
                shouldFlushAgain = !stopAfterCurrentMessage &&
                    !pendingMessages.isEmpty() &&
                    connectionManager.getConnectedClient() != null
            }

            if (shouldFlushAgain) {
                flushPendingMessages()
            }
        }
    }

    private data class ActiveSubscription(
        val client: StompClient,
        val disposable: Disposable
    )
}
