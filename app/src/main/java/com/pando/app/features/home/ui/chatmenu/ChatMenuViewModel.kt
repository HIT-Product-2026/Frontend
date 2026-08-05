package com.pando.app.features.home.ui.chatmenu

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.dto.ConversationDto
import com.pando.app.features.home.data.model.entity.ChatMenuItemModel
import com.pando.app.features.home.data.model.response.ConversationsResponse
import com.pando.app.features.home.data.repository.ConversationRepository
import com.pando.app.features.home.data.socket.ConversationsSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatMenuViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val userSession: UserSession,
    socketConnectionManager: SocketConnectionManager,
    private val conversationsSocket: ConversationsSocket
) : BaseVM<ApiResponse<ConversationsResponse>>() {

    val socketConnectionState: StateFlow<SocketConnectionState> =
        socketConnectionManager.connectionState

    private val _conversations = MutableStateFlow<List<ChatMenuItemModel>>(emptyList())
    val conversations = _conversations.asStateFlow()

    init {
        viewModelScope.launch {
            conversationsSocket.conversationUpdates.collect { event ->
                updateConversation(event)
            }
        }
    }

    fun subscribeConversations() {
        conversationsSocket.subscribe()
    }

    fun unsubscribeConversations() {
        conversationsSocket.unsubscribe()
    }

    private fun updateConversation(
        response: ConversationDto
    ) {
        val oldItem = _conversations.value.firstOrNull { item ->
            item.id == response.id
        } ?: return

        val updatedItem = oldItem.copy(
            previewChat = response.lastMessageContent,
            time = response.lastMessageTime?.toLocalDateTime()
        )

        val updatedList = _conversations.value
            .filterNot { it.id == response.id } + updatedItem

        val sortedItems = updatedList
            .distinctBy { it.id }
            .sortedByDescending { it.time }

        _conversations.value = sortedItems
    }


    fun getConversations() {
        getData {
            val result = conversationRepository.getConversation()

            if (result is DataResult.Success) {

                val total = result.data.data.total

                val data = result.data.data.items
                if (total > 0) {
                    val conversations = data.map { item ->
                        ChatMenuItemModel(
                            id = item.id,
                            senderId =
                                if (getCurrentUserId()?.equals(item.user1.id) == true) item.user1.id else item.user2.id,
                            recipientId =
                                if (getCurrentUserId()?.equals(item.user1.id) == true) item.user2.id else item.user1.id,
                            name = if (getCurrentUserId()?.equals(item.user1.id) == true) {
                                item.user2.displayName.ifEmpty { item.user2.username }
                            } else {
                                item.user1.displayName.ifEmpty { item.user1.username }
                            },
                            previewChat = item.lastMessageContent,
                            time = item.lastMessageTime?.toLocalDateTime()
                        )
                    }

                    val sortedItems = conversations
                        .distinctBy { it.id }
                        .sortedByDescending { it.time }

                    _conversations.value = sortedItems
                }
            }

            result
        }
    }

    fun getCurrentUserId(): UUID? {
        return userSession.getCurrentUserId()
    }
}
