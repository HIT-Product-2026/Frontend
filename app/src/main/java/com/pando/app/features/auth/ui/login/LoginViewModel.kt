package com.pando.app.features.auth.ui.login

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.R
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.model.response.LoginResponse
import com.pando.app.features.auth.data.repository.AuthRepository
import com.pando.app.features.home.data.model.entity.CurrentUser
import com.pando.app.features.shared.AvatarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.tasks.await

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val avatarRepository: AvatarRepository,
    private val userSession: UserSession
) : BaseVM<ApiResponse<LoginResponse>>() {
    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData {
            val loginResult = authRepository.login(email, password)

            if (loginResult is DataResult.Success) {
                val response = loginResult.data.data
                saveAvatarOfUserSession(response)

                sendEvent(ViewModelEvent.ShowSnackbar("Đăng nhập thành công!"))

                getAndSendFcmToken()

                sendEvent(ViewModelEvent.Navigate(R.id.action_loginBottomSheet_to_centerFragment))
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

    private suspend fun saveAvatarOfUserSession(response: LoginResponse) {
        val user = response.user

        userSession.setCurrentUser(
            CurrentUser(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                mode = user.mode
            )
        )

        when (val avatarResult = avatarRepository.getUserAvatar(user.id)) {
            is DataResult.Success -> {
                userSession.updateAvatar(avatarResult.data.data)
            }

            is DataResult.Error -> {

            }
        }
    }
}