package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import java.util.UUID

data class ChatMenuItemModel (
    override val id: UUID,
    val conversationId: UUID,
    val recipientId: UUID,
    val name: String?,
    val previewChat : String?,
    val time : String?
) : BaseItemModel

class DataChatMenuItem {
    companion object {
        var total : Int? = null
        val data : MutableList<ChatMenuItemModel> = mutableListOf()
    }
}
