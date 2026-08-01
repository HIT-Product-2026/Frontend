package com.pando.app.features.home.data.model.request

import java.util.UUID

data class SendImageRequest(
    val conversationId: UUID,
    val postImageUrl: String,
    val isRead: Boolean = false
)