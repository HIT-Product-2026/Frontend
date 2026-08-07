package com.pando.app.core.location

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFERENCE_NAME,
        Context.MODE_PRIVATE
    )

    fun isTrackingEnabled(): Boolean =
        // Chia sẻ vị trí là trạng thái mặc định. Khi người dùng tắt trong
        // PrivacyFragment, giá trị false sẽ được lưu lại rõ ràng.
        preferences.getBoolean(KEY_TRACKING_ENABLED, true)

    fun setTrackingEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_TRACKING_ENABLED, enabled)
        }
    }

    companion object {
        private const val PREFERENCE_NAME = "pando_location_tracking"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    }
}
