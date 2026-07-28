package com.pando.app.features.home.data.model.response

import java.util.UUID

data class LocationUserResponseSocket(
    val userId: UUID?,
    val latitude : Double?,
    val longitude : Double?,
    val lastActiveAt: String?
)
