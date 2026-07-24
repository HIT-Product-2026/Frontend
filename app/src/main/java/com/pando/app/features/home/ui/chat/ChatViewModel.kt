package com.pando.app.features.home.ui.chat

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.ChatMessageItemModel
import com.pando.app.features.home.data.model.entity.DataChatMessageItem
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import com.pando.app.features.home.data.model.response.MessagePageResponse
import com.pando.app.features.home.data.repository.ConversationRepository
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
    private val userSession: UserSession
) : BaseVM<ChatEvent>() {

    private var isLoading = false

    private val _images = MutableStateFlow<Map<UUID, ByteArray>>(emptyMap())

    val images = _images.asStateFlow()

    private val loadingIds = mutableSetOf<UUID>()

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
        if (isLoading) return

        if (DataChatMessageItem.hasLoadedFirstPage &&
            DataChatMessageItem.nextCursor == null
        ) {
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
                                    createdAt = message.createdAt
                                )
                            }

                            MessageType.IMAGE -> {
                                ChatMessageItemModel(
                                    id = message.id,
                                    conversationId = conversationId,
                                    senderId = message.sender.id,
                                    recipientId = recipientId,
                                    type = MessageType.IMAGE,
                                    createdAt = message.createdAt
                                )
                            }
                        }
                    }

                DataChatMessageItem.data.addAll(newMessages)
                DataChatMessageItem.hasLoadedFirstPage = true
                DataChatMessageItem.nextCursor = result.data.data.cursor
            }

            isLoading = false

            when (result) {
                is DataResult.Success -> DataResult.Success(ChatEvent.GetChatHistoryEvent(result.data))
                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun sendTextMessage(conversationId: UUID, content: String) {
        getData {
            when (val result = conversationRepository.sendTextMessage(conversationId, content)) {
                is DataResult.Success -> DataResult.Success(ChatEvent.SendTextEvent(result.data))
                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }
}

sealed interface ChatEvent {
    data class GetChatHistoryEvent(val response: ApiResponse<MessagePageResponse>) : ChatEvent
    data class SendTextEvent(val response: ApiResponse<ChatMessageResponse>) : ChatEvent
}