package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.FriendshipDto

data class RequestedUsersResponse(
    val total: Int,
    val items: List<FriendshipDto>
)
