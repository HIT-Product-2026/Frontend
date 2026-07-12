package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import java.util.UUID

data class SearchItemModel (
    override val id: UUID,
    val name: String,
) : BaseItemModel

class DataSearchItem {
    companion object {
        var total : Int? = null
        val data : MutableList<SearchItemModel> = mutableListOf()
    }
}