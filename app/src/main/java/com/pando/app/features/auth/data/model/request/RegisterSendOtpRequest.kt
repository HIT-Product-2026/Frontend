package com.pando.app.features.auth.data.model.request

import com.google.gson.annotations.SerializedName

data class RegisterSendOtpRequest (
    @SerializedName("email")
    val email : String ?= null,
    @SerializedName("password")
    val password : String ?=null
)