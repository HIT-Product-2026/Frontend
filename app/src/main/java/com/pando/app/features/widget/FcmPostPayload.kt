package com.pando.app.features.widget

import java.util.UUID

data class FcmPostPayload(
    val senderId: String,
    val postId: String,
    val type: String,
    val displayName: String,
    val avatarUrl: String,
    val imageUrl: String,
    val latitude: Double?,
    val longitude: Double?,
    val provinceName: String,
    val wardName: String,
    val caption: String
) {
    companion object {
        fun from(data: Map<String, String>): FcmPostPayload? {
            val senderId = data["sender_id"] ?: return null
            val postId = data["post_id"] ?: return null
            val type = data["type"] ?: return null

            return FcmPostPayload(
                senderId = senderId,
                postId = postId,
                type = type,
                displayName = data["display_name"].orEmpty(),
                avatarUrl = data["avatar_url"].orEmpty(),
                imageUrl = data["image_url"].orEmpty(),
                latitude = data["latitude"]?.toDoubleOrNull(),
                longitude = data["longitude"]?.toDoubleOrNull(),
                provinceName = data["province_name"].orEmpty(),
                caption = data["caption"].orEmpty(),
                wardName = data["ward_name"].orEmpty()
            )
        }
    }
}