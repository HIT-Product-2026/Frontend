package com.pando.app.features.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.pando.app.MainActivity
import com.pando.app.R

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    widgetSize: PostWidgetSize
) {
    val isLargeWidget = widgetSize == PostWidgetSize.LARGE

    val layoutId = if (isLargeWidget) {
        R.layout.widget_post_4x2
    } else {
        R.layout.widget_post_2x2
    }

    val views = RemoteViews(context.packageName, layoutId)
    val storage = WidgetStorage(context)

    val post = storage.getPost()

    if (post == null) {
        views.setTextViewText(R.id.tvCaption, "Chưa có bài đăng mới")

        views.setTextViewText(R.id.tvLocation, "")

        appWidgetManager.updateAppWidget(appWidgetId, views)

        return
    }

    bindCommonData(views, post)

    if (isLargeWidget) {
        bindLargeData(views, post)
        views.setOnClickPendingIntent(
            R.id.btnDirection,
            WidgetPendingIntentFactory.createDirection(context, post.latitude, post.longitude)
        )
        views.setOnClickPendingIntent(
            R.id.btnReply,
            WidgetPendingIntentFactory.createReply(context)
        )
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val openAppPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)

    WidgetImageLoader.loadPostImage(context, views, appWidgetId, post.imageUrl)
    WidgetImageLoader.loadAvatar(context, views, appWidgetId, post.avatarUrl)
}

private fun bindCommonData(views: RemoteViews, post: FcmPostPayload) {
    views.setTextViewText(R.id.tvCaption, post.caption)
    views.setViewVisibility(R.id.background, if (post.caption.isBlank()) View.GONE else View.VISIBLE)
    views.setTextViewText(R.id.tvLocation, post.wardName.ifBlank { post.provinceName })
}

private fun bindLargeData(views: RemoteViews, post: FcmPostPayload) {
    views.setTextViewText(R.id.tvUserName, post.displayName)
    views.setViewVisibility(R.id.tvCaption, if (post.caption.isBlank()) View.GONE else View.VISIBLE)
}