package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class MessagePageResponse(
    override val total: Int,
    override val items: List<ChatMessageResponse>,
    val cursor: String?
) : ListAndTotalInterface<ChatMessageResponse>