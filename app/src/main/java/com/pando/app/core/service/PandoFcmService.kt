package com.pando.app.core.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pando.app.features.widget.FcmPostPayload
import com.pando.app.features.widget.WidgetUpdater

class PandoFcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "PandoFcmService"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Có thông báo từ Server!")

        if (message.data.isEmpty()) {
            Log.w(TAG, "FCM không có data payload")
            return
        }

        val payload = FcmPostPayload.from(message.data)

        if (payload == null) {
            Log.e(TAG, "Không thể chuyển FCM data thành FcmPostPayload")
            Log.e(TAG, "FCM data: ${message.data}")
            return
        }

        when (payload.type) {
            "POST" -> {
                Log.d(TAG, "Bài viết mới: $payload")

                updatePostWidget(payload)
            }
        }
    }

    private fun updatePostWidget(payload: FcmPostPayload) {
        Log.d(TAG, "Post ID: ${payload.postId}")
        Log.d(TAG, "Người đăng: ${payload.displayName}")
        Log.d(TAG, "Ảnh: ${payload.imageUrl}")
        Log.d(TAG, "Địa điểm: ${payload.provinceName}")
        Log.d(TAG, "Caption: ${payload.caption}")

        WidgetUpdater(applicationContext).updatePost(payload)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Mã thiết bị mới: $token")
    }
}