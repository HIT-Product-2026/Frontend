package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserDto
import com.pando.app.features.home.data.model.entity.PostModeLocation

data class PostResponse (
    val id : String,
    val user: UserDto,
    val caption: String,
    val imageUrl: String,
    val contentType: String,
    val latitude: Double,
    val longitude: Double,
    val modeLocation: PostModeLocation
)