package com.pando.app.features.auth.data.model.response

import com.google.gson.annotations.SerializedName

data class LoginResponse (
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("id")
    val id: String,
    @SerializedName("tokenType")
    val tokenType: String
)