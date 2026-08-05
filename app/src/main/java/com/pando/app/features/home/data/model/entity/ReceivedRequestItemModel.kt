package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import com.pando.app.features.home.ui.friend.FriendAction
import java.util.UUID

data class ReceivedRequestItemModel (
    override val id: UUID,
    val name: String,
    val friendshipId: UUID,
    val loadingAction: FriendAction? = null,
    val errorMessage: String? = null
) : BaseItemModel
