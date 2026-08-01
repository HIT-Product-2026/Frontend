package com.pando.app.features.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.pando.app.MainActivity

object WidgetPendingIntentFactory {
    private const val REQUEST_DIRECTION = 1001
    private const val REQUEST_REPLY = 1002

    const val ACTION_OPEN_POST_REEL = "com.pando.app.action.OPEN_POST_REEL"

    fun createDirection(context: Context, latitude: Double?, longitude: Double?): PendingIntent {
        val uri = "geo:$latitude,$longitude?q=$latitude,$longitude".toUri()

        val intent = Intent(Intent.ACTION_VIEW, uri)

        return PendingIntent.getActivity(
            context,
            REQUEST_DIRECTION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createReply(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_POST_REEL

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_REPLY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}