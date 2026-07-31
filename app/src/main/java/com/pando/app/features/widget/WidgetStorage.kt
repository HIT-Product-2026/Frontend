package com.pando.app.features.widget

import android.content.Context
import androidx.core.content.edit

class WidgetStorage(context: Context) {
    companion object {
        private const val PREF_NAME = "pando_widget_preferences"

        private const val KEY_POST_ID = "post_id"
        private const val KEY_IMAGE_URL = "image_url"
        private const val KEY_CONTENT = "content"
        private const val KEY_SENDER_ID = "sender_id"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_SENDER_NAME = "sender_name"
        private const val KEY_PROVINCE_NAME = "province_name"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_TYPE = "type"
    }

    private val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun savePost(payload: FcmPostPayload) {
        pref.edit {
            putString(KEY_SENDER_ID, payload.senderId)
            putString(KEY_POST_ID, payload.postId)
            putString(KEY_TYPE, payload.type)
            putString(KEY_SENDER_NAME, payload.displayName)
            putString(KEY_AVATAR_URL, payload.avatarUrl)
            putString(KEY_IMAGE_URL, payload.imageUrl)
            putString(KEY_PROVINCE_NAME, payload.provinceName)
            putString(KEY_CONTENT, payload.caption)
            putString(KEY_LATITUDE, payload.latitude?.toString().orEmpty())
            putString(KEY_LONGITUDE, payload.longitude?.toString().orEmpty())
        }
    }

    fun getPost(): FcmPostPayload? {
        val senderId = pref.getString(KEY_SENDER_ID, null)
            ?: return null

        val postId = pref.getString(KEY_POST_ID, null)
            ?: return null

        val type = pref.getString(KEY_TYPE, null)
            ?: return null

        return FcmPostPayload(
            senderId = senderId,
            postId = postId,
            type = type,
            displayName = pref.getString(KEY_SENDER_NAME, "").orEmpty(),
            avatarUrl = pref.getString(KEY_AVATAR_URL, "").orEmpty(),
            imageUrl = pref.getString(KEY_IMAGE_URL, "").orEmpty(),
            latitude = pref.getString(KEY_LATITUDE, null)?.toDoubleOrNull(),
            longitude = pref.getString(KEY_LONGITUDE, null)?.toDoubleOrNull(),
            provinceName = pref.getString(KEY_PROVINCE_NAME, "").orEmpty(),
            caption = pref.getString(KEY_CONTENT, "") .orEmpty()
        )
    }

    fun clear() {
        pref.edit {
            clear()
        }
    }
}