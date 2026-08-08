package com.pando.app.features.auth.ui.login

import com.pando.app.R
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.service.FcmTokenSyncManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.model.response.LoginResponse
import com.pando.app.features.auth.data.repository.AuthRepository
import com.pando.app.features.home.data.model.entity.CurrentUser
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSession: UserSession,
    private val fcmTokenSyncManager: FcmTokenSyncManager
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
                saveUserSession(response)
                fcmTokenSyncManager.syncAfterAuthentication()

                sendEvent(ViewModelEvent.ShowSnackbar("Đăng nhập thành công!"))

                sendEvent(ViewModelEvent.Navigate(R.id.action_loginBottomSheet_to_centerFragment))
            }

            loginResult
        }
    }

    private fun saveUserSession(response: LoginResponse) {
        val user = response.user

        userSession.setCurrentUser(
            CurrentUser(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                mode = user.mode,
                avatar = user.avatarUrl?.takeIf(String::isNotBlank)
            )
        )
    }
}
