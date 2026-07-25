package com.pando.app.features.home.ui.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.extensions.formatDateTime
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.ChatMessageItemModel
import com.pando.app.features.home.data.model.entity.DataChatMessageItem
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import com.pando.app.features.home.data.model.response.MessagePageResponse
import com.pando.app.features.home.data.repository.ConversationRepository
import com.pando.app.features.home.data.socket.MessagesSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    socketConnectionManager: SocketConnectionManager,
    private val messagesSocket: MessagesSocket
) : BaseVM<ApiResponse<MessagePageResponse>>() {

    private val _images = MutableStateFlow<Map<UUID, ByteArray>>(emptyMap())
    val images = _images.asStateFlow()

    val socketConnectionState = socketConnectionManager.connectionState

    private val _messages = MutableStateFlow<List<ChatMessageItemModel>>(emptyList())
    val messages = _messages.asStateFlow()

    private lateinit var currentConversationId: UUID
    private lateinit var currentRecipientId: UUID

    private var isLoading = false

    private val loadingIds = mutableSetOf<UUID>()

    init {
        viewModelScope.launch {
            messagesSocket.message.collect { message ->
                updateMessages(message)
            }
        }
    }

    fun setCurrentConversationId(conversationId: UUID) {
        currentConversationId = conversationId
    }

    fun setCurrentRecipientId(recipientId: UUID) {
        currentRecipientId = recipientId
    }

    fun subscribeMessage() {
        messagesSocket.subscribeConversation(currentConversationId)
    }

    fun unsubscribeMessage() {
        messagesSocket.unsubscribeConversation(currentConversationId)
    }

    fun sendMessage(conversationId: UUID, content: String) {
        messagesSocket.sendMessage(conversationId, content)
    }

    fun updateMessages(
        message: ChatMessageResponse
    ) {
        Log.d("MessageSocket", "Dang cap nhat")
        _messages.update { currentList ->
            val newMessage = ChatMessageItemModel(
                id = message.id,
                senderId = message.sender.id,
                content = message.content,
                createdAt = message.createdAt.toLocalDateTime(),
                type = message.type,
                conversationId = currentConversationId,
                recipientId = currentRecipientId
            )

            val list = (currentList + newMessage)
                .associateBy { it.id }
                .values
                .sortedBy { it.createdAt }

            Log.d("MessageSocket", "Đã tạo danh sách messages mới")
            list
        }
        Log.d("MessageSocket", "Cập nhật thành công lên data")
    }

    fun loadImageMessage(messageId: UUID) {
        if (_images.value.containsKey(messageId)) {
            return
        }
        if (!loadingIds.add(messageId)) return

        viewModelScope.launch {
            when (val result = conversationRepository.getImageMessage(messageId)) {
                is DataResult.Success -> {
                    _images.update { current ->
                        current + (messageId to result.data)
                    }
                }

                is DataResult.Error -> {
                    // Emit event
                }
            }

            loadingIds.remove(messageId)
        }
    }

    fun loadImageMessages(messageIds: Collection<UUID>) {
        messageIds.distinct().forEach(::loadImageMessage)
    }

    fun getMessageList(conversationId: UUID, recipientId: UUID) {
        if (isLoading) {
            Log.d("OkHttp", "Đang load danh sách tin ")
            return
        }

        if (DataChatMessageItem.hasLoadedFirstPage &&
            DataChatMessageItem.nextCursor?.isBlank() == true
        ) {
            Log.d("OkHttp", "Đã hết trang")
            return
        }

        isLoading = true
        val requestedCursor = DataChatMessageItem.nextCursor

        getData {
            val result =
                conversationRepository.getConversationMessages(conversationId, requestedCursor)

            if (result is DataResult.Success) {
                val response = result.data.data

                DataChatMessageItem.total = DataChatMessageItem.total?.plus(response.total)

                val existingIds = DataChatMessageItem.data
                    .mapTo(hashSetOf()) { it.id }

                val newMessages = response.items
                    .filter { existingIds.add(it.id) }
                    .map { message ->
                        when (message.type) {
                            MessageType.TEXT -> {
                                ChatMessageItemModel(
                                    id = message.id,
                                    conversationId = conversationId,
                                    senderId = message.sender.id,
                                    recipientId = recipientId,
                                    content = message.content,
                                    type = MessageType.TEXT,
                                    createdAt = message.createdAt.toLocalDateTime()
                                )
                            }

                            MessageType.IMAGE -> {
                                ChatMessageItemModel(
                                    id = message.id,
                                    conversationId = conversationId,
                                    senderId = message.sender.id,
                                    recipientId = recipientId,
                                    type = MessageType.IMAGE,
                                    createdAt = message.createdAt.toLocalDateTime()
                                )
                            }
                        }
                    }

                _messages.update { current ->
                    (current + newMessages)
                        .associateBy { it.id }
                        .values
                        .sortedWith(
                            compareBy<ChatMessageItemModel> { it.createdAt }
                                .thenBy { it.id.toString() }
                        )
                }

                loadImageMessages(
                    newMessages
                        .filter { it.type == MessageType.IMAGE }
                        .map { it.id }
                )

                DataChatMessageItem.hasLoadedFirstPage = true
                DataChatMessageItem.nextCursor = result.data.data.cursor
            }

            isLoading = false

            result
        }
    }

//    fun sendTextMessage(conversationId: UUID, content: String) {
//        getData {
//            when (val result = conversationRepository.sendTextMessage(conversationId, content)) {
//                is DataResult.Success -> DataResult.Success(ChatEvent.SendTextEvent(result.data))
//                is DataResult.Error -> DataResult.Error(result.message)
//            }
//        }
//    }

//    private fun emitEvent(event: ChatEvent) {
//        viewModelScope.launch {
//            _chatEvent.emit(event)
//        }
//    }
}