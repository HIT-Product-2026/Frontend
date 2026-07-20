package com.pando.app.features.home.data.model.request

import java.util.UUID

data class SendMessageRequest(
    val conversationId: UUID,
    val content: String
)