package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.features.home.data.model.request.SendImageRequest
import com.pando.app.features.home.data.model.request.SendMessageRequest
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
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

    private val conversationSubscriptions = mutableMapOf<UUID, Disposable>()

    private val _message = MutableSharedFlow<ChatMessageResponse>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val message = _message.asSharedFlow()

    fun subscribeConversation(conversationId: UUID) {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
            return
        }

        if (conversationSubscriptions.containsKey(conversationId)) {
            Log.d(TAG, "Conversation $conversationId đã subscribe")
            return
        }

        val destination = "${SocketConstants.Chat.TOPIC_CONVERSATION}/$conversationId"
        Log.d(TAG, "Subscribe destination: $destination")

        val disposable = client
            .topic(destination)
            .subscribe(
                { topicMessage ->
                    Log.d(TAG, "Nhận message: ${topicMessage.payload}")

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

        conversationSubscriptions[conversationId] = disposable
    }

    fun unsubscribeConversation(conversationId: UUID) {
        conversationSubscriptions.values.forEach { disposable ->
            disposable.dispose()
        }
        conversationSubscriptions.clear()

        Log.d(TAG, "Đã unsubscribe conversation $conversationId")
    }

    fun sendMessage(conversationId: UUID, content: String) {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
            return
        }

        if (content.isBlank()) {
            Log.e(TAG, "Nội dung tin nhắn đang trống")
            return
        }

        val request = SendMessageRequest(
            conversationId = conversationId,
            content = content.trim()
        )

        val payload = gson.toJson(request)

        client.send(
            SocketConstants.Chat.SEND_TEXT_DESTINATION,
            payload
        ).subscribe(
            {
                Log.d(TAG, "Đã gửi message lên STOMP")
            },
            { throwable ->
                Log.e(TAG, "Không thể gửi message", throwable)
            }
        )
    }

    fun sendImageMessage(conversationId: UUID, postImageUrl: String) {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
            return
        }

        if (postImageUrl.isBlank()) {
            Log.e(TAG, "Không có link ảnh")
            return
        }

        val request = SendImageRequest(
            conversationId = conversationId,
            postImageUrl = postImageUrl
        )

        val payload = gson.toJson(request)

        client.send(
            SocketConstants.Chat.SEND_IMAGE_DESTINATION,
            payload
        ).subscribe(
            {
                Log.d(TAG, "Đã gửi message lên STOMP")
            },
            { throwable ->
                Log.e(TAG, "Không thể gửi message", throwable)
            }
        )
    }
}