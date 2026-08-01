package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.features.home.data.model.dto.ConversationDto
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _conversationUpdates = MutableSharedFlow<ConversationDto>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val conversationUpdates = _conversationUpdates.asSharedFlow()

    private var subscription: Disposable? = null

    fun subscribe() {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Socket chưa kết nối")
            return
        }

        if (subscription != null) {
            Log.d(TAG, "Đã subscribe conversation updates rồi")
            return
        }

        val destination = SocketConstants.Chat.USER_QUEUE_CONVERSATIONS
        Log.d(TAG, "Subscribe destination: $destination")

        subscription = client
            .topic(destination)
            .subscribe(
                { message ->
                    Log.d(TAG, "Nhận conversation update: ${message.payload}")

                    val conversation = runCatching {
                        gson.fromJson(message.payload, ConversationDto::class.java)
                    }.getOrElse { throwable ->
                        Log.e(TAG, "Không parse được conversation", throwable)
                        null
                    }

                    conversation?.let {
                        _conversationUpdates.tryEmit(it)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Lỗi subscribe $destination", throwable)
                }
            )

        Log.d(TAG, "Đã subscribe $destination")
    }

    fun unsubscribe() {
        subscription?.dispose()
        subscription = null

        Log.d(TAG, "Đã unsubscribe conversation")
    }
}