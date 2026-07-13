package com.pando.app.features.home.data.model.dto

import com.pando.app.features.home.data.model.entity.enumEntity.FriendshipStatus
import java.util.UUID

data class FriendshipDto (
    val id : UUID,
    val requester : UserDto,
    val receiver : UserDto,
    val status : FriendshipStatus
)