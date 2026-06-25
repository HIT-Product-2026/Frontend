package com.pando.app.features.auth.data.model.response

data class RegisterResponse (
    val id: String?=null,
    val username: String?=null,
    val email: String?=null,
    val displayName: String?=null,
    val latitude: Double?=null,
    val longitude: Double?=null,
    val fcmToken: String?=null
)