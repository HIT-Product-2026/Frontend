package com.pando.app.features.home.data.model.dto

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class UserSearchResultDTO(
    @SerializedName("userId")
    val id: UUID,
    val displayName: String,
    val avatarUrl: String?
)