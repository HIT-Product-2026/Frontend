package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import java.util.UUID

data class FriendItemModel (
    override val id: UUID,
    val name: String,
    val avatarUrl: String?=null,
    val longitude: Double?=null,
    val latitude: Double?=null,
    val lastActiveAt: String? = null
) : BaseItemModel

class DataFriendItem {
    companion object {
        var total : Int? = null
        val data : MutableList<FriendItemModel> = mutableListOf()
    }
}