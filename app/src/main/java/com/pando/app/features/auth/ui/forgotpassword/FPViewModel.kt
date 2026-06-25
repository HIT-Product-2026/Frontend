package com.pando.app.features.auth.ui.forgotpassword

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.ui.UiState
import com.pando.app.features.auth.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class FPViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseVM<ApiResponse<Void>>() {
    fun forgotPassword(email: String) {
        if (email.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData { authRepository.forgotPasswordSendOtp(email) }
    }
}