package com.pando.app.features.auth.ui.login

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.ui.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.model.response.LoginResponse
import com.pando.app.features.auth.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseVM<ApiResponse<LoginResponse>>() {
    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData {
            val loginResult = authRepository.login(email, password)

            if (loginResult is DataResult.Success) {
                viewModelScope.launch {
                    getAndSendFcmToken()
                }
            }

            loginResult
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getAndSendFcmToken() {
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()

            if (!fcmToken.isNullOrEmpty()) {
                authRepository.sendFcmToken(fcmToken)
                Log.d("FCM", "FCM Token lấy thành công: $fcmToken")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}