package com.pando.app.core.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pando.app.core.session.UserSession
import com.pando.app.features.auth.data.repository.AuthRepository
import com.pando.app.features.widget.FcmPostPayload
import com.pando.app.features.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PandoFcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "PandoFcmService"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var userSession: UserSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                Log.d(TAG, "Đã nhận thông báo bài viết mới")

                updatePostWidget(payload)
            }
        }
    }

    private fun updatePostWidget(payload: FcmPostPayload) {
        WidgetUpdater(applicationContext).updatePost(payload)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token đã được làm mới")

        if (userSession.getCurrentUser() == null) return
        serviceScope.launch {
            authRepository.sendFcmToken(token)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
