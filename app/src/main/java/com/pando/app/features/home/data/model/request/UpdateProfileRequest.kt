package com.pando.app.features.home.data.model.request

import com.pando.app.features.home.data.model.entity.enumEntity.Gender

data class UpdateProfileRequest (
    val birthday: String?=null,
    val gender: Gender?=null,
    val phoneNumber: String?=null
)