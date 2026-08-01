package com.pando.app.features.home.data.model.dto

import java.util.UUID

data class Conversation(
    val id : UUID,
    val user1: UserDto,
    val user2: UserDto,
)
