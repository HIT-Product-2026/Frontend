package com.pando.app.features.widget

import android.content.Context
import android.util.Log
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus

class WidgetUpdater(context: Context) {
    private val appContext = context.applicationContext

    private val widgetStorage = WidgetStorage(appContext)

    fun updatePost(payload: FcmPostPayload) {
        if (payload.nsfw == NsfwStatus.TRUE) {
            // Không ghi đè bài đang hiển thị; widget tiếp tục giữ bài trước đó.
            Log.d(TAG, "Bỏ qua bài NSFW ${payload.postId} trên widget")
            return
        }

        widgetStorage.savePost(payload)

        PostWidgetSmall.updateAllWidgets(appContext)
        PostWidgetLarge.updateAllWidgets(appContext)
    }

    private companion object {
        const val TAG = "WidgetUpdater"
    }
}
