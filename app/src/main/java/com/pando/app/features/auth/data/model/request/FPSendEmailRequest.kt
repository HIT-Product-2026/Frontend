package com.pando.app.features.auth.data.model.request

import com.google.gson.annotations.SerializedName

data class FPSendEmailRequest (
    @SerializedName("email")
    val email: String?= null
)