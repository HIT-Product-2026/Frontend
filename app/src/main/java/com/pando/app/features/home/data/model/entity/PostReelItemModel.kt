package com.pando.app.features.home.data.model.entity

import com.pando.app.core.base.BaseItemModel
import com.pando.app.features.home.data.model.dto.UserDto
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.entity.enumEntity.PostModeLocation
import java.time.LocalDateTime
import java.util.UUID

data class PostReelItemModel(
    override val id: UUID,
    val conversationId: UUID?=null,
    val user: UserDto,
    val caption: String?,
    val latitude: Double?,
    val longitude: Double?,
    val modeLocation: PostModeLocation,
    val nsfw: NsfwStatus?= NsfwStatus.PROCESSING,
    val createdAt: LocalDateTime?
) : BaseItemModel

class DataPostReelItem {
    companion object {
        var total: Int? = null
        var nextCursor: String? = ""
        var hasLoadedFirstPage = false

        val data: MutableList<PostReelItemModel> = mutableListOf()

        fun reset() {
            total = null
            nextCursor = ""
            hasLoadedFirstPage = false
            data.clear()
        }
    }
}