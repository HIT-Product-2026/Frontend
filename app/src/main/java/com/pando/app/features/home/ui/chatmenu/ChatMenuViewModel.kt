package com.pando.app.features.home.ui.chatmenu

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.session.UserSession
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.ChatMenuItemModel
import com.pando.app.features.home.data.model.entity.DataChatMenuItem
import com.pando.app.features.home.data.model.response.ConversationsResponse
import com.pando.app.features.home.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatMenuViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val userSession: UserSession
) : BaseVM<ApiResponse<ConversationsResponse>>() {

    fun getConversations() {
        getData {
            val result = conversationRepository.getConversation()

            if (result is DataResult.Success) {

                DataChatMenuItem.apply {
                    data.clear()
                    total = 0
                }

                val total = result.data.data.total
                DataChatMenuItem.total = total

                val data = result.data.data.items
                if (total > 0) {
                    data.forEach { item ->
                        DataChatMenuItem.data.add(
                            ChatMenuItemModel(
                                id = if (getCurrentUserId()?.equals(item.user1.id) == true) {
                                    item.user1.id
                                } else {
                                    item.user2.id
                                },
                                conversationId = item.id,
                                recipientId = if (getCurrentUserId()?.equals(item.user1.id) == true) {
                                    item.user2.id
                                } else {
                                    item.user1.id
                                },
                                name = if (getCurrentUserId()?.equals(item.user1.id) == true) {
                                    item.user2.displayName.ifEmpty { item.user2.username }
                                } else {
                                    item.user1.displayName.ifEmpty { item.user1.username }
                                },
                                previewChat = item.lastMessageContent,
                                time = item.lastMessageTime
                                )
                        )
                    }
                }
            }

            result
        }
    }

    fun getCurrentUserId(): UUID? {
        return userSession.getCurrentUserId()
    }
}