package com.pando.app.features.auth.data.model.request

import com.google.gson.annotations.SerializedName

data class FPResetPasswordRequest (
    @SerializedName("email")
    val email : String ?= null,
    @SerializedName("newPassword")
    val password : String ?= null,
)