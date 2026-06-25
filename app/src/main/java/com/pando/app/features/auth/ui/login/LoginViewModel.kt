package com.pando.app.features.auth.ui.login

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.ui.UiState
import com.pando.app.features.auth.data.model.response.LoginResponse
import com.pando.app.features.auth.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseVM<ApiResponse<LoginResponse>>() {
    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData { authRepository.login(email, password) }
    }
}