package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserDto
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import java.util.UUID

data class ChatMessageResponse(
    val id: UUID,
    val sender: UserDto,
    val conversationId: UUID,
    val imageUrl: String?,
    val content: String?,
    val type: MessageType,
    val createdAt: String
)
