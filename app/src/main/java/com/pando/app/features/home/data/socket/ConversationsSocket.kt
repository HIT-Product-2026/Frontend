package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.features.home.data.model.dto.ConversationDto
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.StompClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationsSocket @Inject constructor(
    private val gson: Gson,
    private val connectionManager: SocketConnectionManager,
) {
    companion object {
        private const val TAG = "ConversationSocket"
    }

    private var subscribedClient: StompClient? = null
    private var shouldSubscribe = false
    private var retryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val conversationChannel = Channel<ConversationDto>(capacity = 256)
    val conversationUpdates = conversationChannel.receiveAsFlow()

    private var subscription: Disposable? = null

    init {
        scope.launch {
            connectionManager.connectionState.collectLatest { state ->
                if (state == com.pando.app.core.state.SocketConnectionState.Connected &&
                    shouldSubscribe
                ) {
                    subscribeInternal()
                } else if (state != com.pando.app.core.state.SocketConnectionState.Connected) {
                    clearActiveSubscription()
                }
            }
        }
    }

    @Synchronized
    fun subscribe() {
        shouldSubscribe = true
        subscribeInternal()
    }

    @Synchronized
    private fun subscribeInternal() {
        val client = connectionManager.getConnectedClient() ?: run {
            return
        }

        if (subscribedClient === client && subscription?.isDisposed == false) {
            Log.d(TAG, "Client hiện tại đã subscribe conversation")
            return
        }

        clearActiveSubscription()

        val destination = SocketConstants.Chat.USER_QUEUE_CONVERSATIONS
        Log.d(TAG, "Subscribe destination: $destination")

        subscription = client
            .topic(destination)
            .subscribe(
                { message ->
                    val conversation = runCatching {
                        gson.fromJson(message.payload, ConversationDto::class.java)
                    }.getOrElse { throwable ->
                        Log.e(TAG, "Không parse được conversation", throwable)
                        null
                    }

                    conversation?.let {
                        if (!conversationChannel.trySend(it).isSuccess) {
                            Log.e(TAG, "Bộ đệm conversation đã đầy")
                        }
                    }
                },
                { throwable ->
                    Log.e(TAG, "Lỗi subscribe $destination", throwable)
                    handleSubscriptionFailure(client)
                }
            )

        subscribedClient = client
        Log.d(TAG, "Đã subscribe $destination")
    }

    @Synchronized
    fun unsubscribe() {
        shouldSubscribe = false
        retryJob?.cancel()
        retryJob = null
        clearActiveSubscription()

        Log.d(TAG, "Đã unsubscribe conversation")
    }

    @Synchronized
    private fun clearActiveSubscription() {
        subscription?.dispose()
        subscription = null
        subscribedClient = null
    }

    private fun handleSubscriptionFailure(client: StompClient) {
        synchronized(this) {
            if (subscribedClient !== client) return
            clearActiveSubscription()
        }

        if (!shouldSubscribe || retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(1_000L)
            synchronized(this@ConversationsSocket) {
                retryJob = null
            }
            if (shouldSubscribe && connectionManager.getConnectedClient() != null) {
                subscribeInternal()
            }
        }
    }
}
