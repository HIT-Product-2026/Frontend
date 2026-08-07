package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import java.util.UUID

data class SentRequestItemModel (
    override val id: UUID,
    val name: String,
    val friendshipId: UUID,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val avatarUrl: String? = null
) : BaseItemModel
