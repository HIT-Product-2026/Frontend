package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserDto
import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class FriendListResponse(
    override val total: Int,
    override val items: List<UserDto>
): ListAndTotalInterface<UserDto>
