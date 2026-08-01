package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import java.time.LocalDateTime
import java.util.UUID

data class ChatMessageItemModel(
    override val id: UUID,
    val conversationId: UUID,
    val senderId: UUID? = null,
    val recipientId: UUID? = null,
    val imageUrl: String? = null,
    val content: String? = null,
    val type: MessageType,
    val createdAt: LocalDateTime
) : BaseItemModel

class DataChatMessageItem {
    companion object {
        var total: Int? = null
        var nextCursor: String? = ""
        var hasLoadedFirstPage = false

        val data: MutableList<ChatMessageItemModel> = mutableListOf()

        fun reset() {
            total = null
            nextCursor = ""
            hasLoadedFirstPage = false
            data.clear()
        }
    }
}