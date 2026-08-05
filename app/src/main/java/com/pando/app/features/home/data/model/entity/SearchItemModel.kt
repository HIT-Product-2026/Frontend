package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import java.util.UUID

data class SearchItemModel (
    override val id: UUID,
    val name: String,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) : BaseItemModel
