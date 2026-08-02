package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.entity.enumEntity.Gender
import java.util.UUID

data class ProfileResponse(
    val id: UUID,
    val userId: UUID,
    val birthday: String?,
    val gender: Gender?,
    val phoneNumber: String?
)
