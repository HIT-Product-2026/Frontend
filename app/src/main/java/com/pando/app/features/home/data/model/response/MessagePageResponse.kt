package com.pando.app.features.home.data.model.response

data class MessagePageResponse(
    override val total: Int,
    override val items: List<ChatMessageResponse>,
    val cursor: String?
) : ListAndTotalInterface<ChatMessageResponse>