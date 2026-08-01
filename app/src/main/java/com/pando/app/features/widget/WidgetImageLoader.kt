package com.pando.app.features.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.AppWidgetTarget
import com.pando.app.R

object WidgetImageLoader {

    fun loadPostImage(context: Context, remoteViews: RemoteViews, appWidgetId: Int, imageUrl: String) {
        if (imageUrl.isBlank()) {
            remoteViews.setImageViewResource(R.id.ivPostImage, R.drawable.ic_default_avatar)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)

            return
        }

        val target = AppWidgetTarget(context, R.id.ivPostImage, remoteViews, appWidgetId)

        Glide.with(context.applicationContext)
            .asBitmap()
            .load(imageUrl)
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(target)
    }

    fun loadAvatar(context: Context, remoteViews: RemoteViews, appWidgetId: Int, avatarUrl: String) {
        if (avatarUrl.isBlank()) {
            remoteViews.setImageViewResource(R.id.profileIcon, R.drawable.ic_default_avatar)

            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
            return
        }

        val target = AppWidgetTarget(context, R.id.profileIcon, remoteViews, appWidgetId)

        Glide.with(context.applicationContext)
            .asBitmap()
            .load(avatarUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(target)
    }
}