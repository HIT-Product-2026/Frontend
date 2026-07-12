package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserDto

data class FriendListResponse(
    val total: Int,
    val items: List<UserDto>
)
