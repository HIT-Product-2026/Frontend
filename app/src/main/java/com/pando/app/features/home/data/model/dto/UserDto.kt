package com.pando.app.features.home.data.model.dto

import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import java.util.UUID

data class UserDto (
    val id: UUID,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val mode: UserMode,
    val fcmToken: String
)