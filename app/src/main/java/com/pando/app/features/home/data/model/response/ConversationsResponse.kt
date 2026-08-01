package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.ConversationDto
import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class ConversationsResponse(
    override val total: Int,
    override val items: List<ConversationDto>
): ListAndTotalInterface<ConversationDto>
