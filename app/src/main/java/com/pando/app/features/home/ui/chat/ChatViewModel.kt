package com.pando.app.features.home.ui.chat

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.ChatMessageItemModel
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    socketConnectionManager: SocketConnectionManager,
    private val messagesSocket: MessagesSocket
) : BaseVM<ApiResponse<MessagePageResponse>>() {

//    private val _images = MutableStateFlow<Map<UUID, String>>(emptyMap())
//    val images = _images.asStateFlow()

    val socketConnectionState = socketConnectionManager.connectionState

    private val _messages = MutableStateFlow<List<ChatMessageItemModel>>(emptyList())
    val messages = _messages.asStateFlow()

    private lateinit var currentConversationId: UUID
    private lateinit var currentRecipientId: UUID

    private var isLoading = false
    private var hasLoadedFirstPage = false
    private var nextCursor: String? = null

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

    fun updateMessages(message: ChatMessageResponse) {
        if (!::currentConversationId.isInitialized ||
            message.conversationId != currentConversationId
        ) {
            return
        }

        _messages.update { currentList ->
            val newMessage = when (message.type) {
                MessageType.TEXT -> {
                    ChatMessageItemModel(
                        id = message.id,
                        conversationId = currentConversationId,
                        senderId = message.sender.id,
                        recipientId = currentRecipientId,
                        content = message.content,
                        type = message.type,
                        createdAt = message.createdAt.toLocalDateTime()
                    )
                }

                MessageType.IMAGE -> {
                    ChatMessageItemModel(
                        id = message.id,
                        senderId = message.sender.id,
                        conversationId = currentConversationId,
                        content = message.imageUrl,
                        createdAt = message.createdAt.toLocalDateTime(),
                        type = message.type,
                        recipientId = currentRecipientId,
                    )
                }
            }

            val list = (currentList + newMessage)
                .associateBy { it.id }
                .values
                .sortedBy { it.createdAt }

            list
        }
    }

//    fun loadImageMessage(messageId: UUID) {
//        if (_images.value.containsKey(messageId)) {
//            return
//        }
//        if (!loadingIds.add(messageId)) return
//
//        viewModelScope.launch {
//            when (val result = conversationRepository.getImageMessage(messageId)) {
//                is DataResult.Success -> {
//                    _images.update { current ->
//                        current + (messageId to result.data.data)
//                    }
//                }
//
//                is DataResult.Error -> {
//                    // Emit event
//                }
//            }
//
//            loadingIds.remove(messageId)
//        }
//    }

//    fun loadImageMessages(messageIds: Collection<UUID>) {
//        messageIds.distinct().forEach(::loadImageMessage)
//    }

    fun getMessageList(conversationId: UUID, recipientId: UUID) {
        if (isLoading) {
            return
        }

        if (hasLoadedFirstPage && nextCursor.isNullOrBlank()) {
            return
        }

        isLoading = true
        val requestedCursor = nextCursor

        getData {
            val result =
                conversationRepository.getConversationMessages(conversationId, requestedCursor)

            if (result is DataResult.Success) {
                val response = result.data.data

                val existingIds = _messages.value
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
                                    imageUrl = message.imageUrl,
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

//                loadImageMessages(
//                    newMessages
//                        .filter { it.type == MessageType.IMAGE }
//                        .map { it.id }
//                )

                hasLoadedFirstPage = true
                nextCursor = result.data.data.cursor
            }

            isLoading = false

            result
        }
    }

    fun canLoadMoreMessages(): Boolean {
        return !hasLoadedFirstPage || !nextCursor.isNullOrBlank()
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
