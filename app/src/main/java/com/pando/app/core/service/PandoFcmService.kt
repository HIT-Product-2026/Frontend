package com.pando.app.core.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PandoFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FCM_RECEIVE", "Có thông báo từ Server!")

        if (message.data.isNotEmpty()) {
            val action = message.data["action"]

            if (action == "WIDGET_UPDATE") {
                val imageUrl = message.data["newImageUrl"]
                Log.d("FCM_RECEIVE", "Cần tải ảnh lên Widget: $imageUrl")
            }
            else if (action == "NEW_CHAT_MESSAGE") {
                val senderName = message.data["senderName"]
                Log.d("FCM_RECEIVE", "Tin nhắn từ $senderName")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Mã thiết bị mới: $token")
    }
}