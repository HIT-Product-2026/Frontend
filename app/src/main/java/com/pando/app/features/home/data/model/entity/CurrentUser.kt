package com.pando.app.features.home.data.model.entity

import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import java.util.UUID

data class CurrentUser(
    val id: UUID,
    val username: String?,
    val displayName: String?,
    val mode: UserMode?,
    val avatar: ByteArray? = null
)