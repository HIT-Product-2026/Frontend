package com.pando.app.features.auth.data.model.request

import com.google.gson.annotations.SerializedName

data class RegisterVerifyOtpRequest (
    @SerializedName("email")
    val email : String ?= null,
    @SerializedName("otp")
    val otp : String ?= null
)