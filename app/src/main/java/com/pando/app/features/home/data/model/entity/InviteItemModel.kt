package com.pando.app.features.home.data.model.entity

import androidx.annotation.DrawableRes
import com.pando.app.R
import com.pando.app.core.base.BaseItemModel

data class InviteItemModel(
    val id: String,
    val name: String,
    @param:DrawableRes val icon: Int
)

class DataInviteItem {
    companion object {
        val data: MutableList<InviteItemModel> = mutableListOf(
            InviteItemModel("facebook_ic", "Facebook", R.drawable.facebook),
            InviteItemModel("messenger_ic", "Messenger", R.drawable.messenger),
            InviteItemModel("instagram_ic", "Instagram", R.drawable.instagram),
            InviteItemModel("message_ic", "Message", R.drawable.message_ic),
            InviteItemModel("more_ic", "More", R.drawable.more)
        )
    }
}
