package com.pando.app.features.auth.data.model.response

import com.google.gson.annotations.SerializedName
import com.pando.app.features.home.data.model.dto.UserDto

data class LoginResponse (
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("user")
    val user: UserDto,
    @SerializedName("tokenType")
    val tokenType: String
)