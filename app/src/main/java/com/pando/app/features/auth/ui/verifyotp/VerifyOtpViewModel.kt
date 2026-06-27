package com.pando.app.features.auth.ui.verifyotp

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.ui.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

sealed interface VerifyOtpResult {
    data class RegisterSuccess(val response: ApiResponse<Void>) : VerifyOtpResult
    data class ForgotPasswordSuccess(val response: ApiResponse<java.lang.Void>) : VerifyOtpResult
}

@HiltViewModel
class VerifyOtpViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseVM<VerifyOtpResult>() {
    fun registerVerify(email: String, otp: String) {
        if (email.isEmpty() || otp.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData {
            when (val result = authRepository.registerVerifyOtp(email, otp)) {
                is DataResult.Success -> DataResult.Success(VerifyOtpResult.RegisterSuccess(result.data))
                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun registerForgotPassword(email: String, otp: String) {
        if (email.isEmpty() || otp.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData {
            when (val result = authRepository.forgotPasswordVerifyOtp(email, otp)) {
                is DataResult.Success -> DataResult.Success(VerifyOtpResult.ForgotPasswordSuccess(result.data))
                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }
}