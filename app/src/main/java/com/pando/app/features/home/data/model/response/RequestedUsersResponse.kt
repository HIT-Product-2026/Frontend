package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.FriendshipDto
import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class RequestedUsersResponse(
    override val total: Int,
    override val items: List<FriendshipDto>
): ListAndTotalInterface<FriendshipDto>
