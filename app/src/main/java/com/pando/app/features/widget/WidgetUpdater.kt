package com.pando.app.features.widget

import android.content.Context

class WidgetUpdater(context: Context) {
    private val appContext = context.applicationContext

    private val widgetStorage = WidgetStorage(appContext)

    fun updatePost(payload: FcmPostPayload) {
        widgetStorage.savePost(payload)

        PostWidgetSmall.updateAllWidgets(appContext)
        PostWidgetLarge.updateAllWidgets(appContext)
    }
}