package com.pando.app.features.home.data.model.dto


data class ConversationDto (
    val user1: UserDto,
    val user2: UserDto,
    val lastMessageContent: String,
    val lastMessageTime: String
)