package com.pando.app.features.auth.data.model.request

import com.google.gson.annotations.SerializedName

data class RefreshTokenRequest(
    @SerializedName("refeshToken")
    val refreshToken: String
)