package com.pando.app.features.auth.ui.verifyotp

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.repository.AuthRepository
import com.pando.app.features.auth.data.store.PendingRegistrationStore
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VerifyOtpResult {
    data class RegisterSuccess(val response: ApiResponse<Void>) : VerifyOtpResult
    data class ForgotPasswordSuccess(val response: ApiResponse<Void>) : VerifyOtpResult
}

sealed interface ResendOtpState {
    data object Idle : ResendOtpState
    data object Loading : ResendOtpState
    data object Success : ResendOtpState
    data class Error(val message: String) : ResendOtpState
}

@HiltViewModel
class VerifyOtpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingRegistrationStore: PendingRegistrationStore
) : BaseVM<VerifyOtpResult>() {
    private val _resendState = MutableStateFlow<ResendOtpState>(ResendOtpState.Idle)
    val resendState: StateFlow<ResendOtpState> = _resendState.asStateFlow()

    private val _resendCooldownSeconds = MutableStateFlow(RESEND_COOLDOWN_SECONDS)
    val resendCooldownSeconds: StateFlow<Int> = _resendCooldownSeconds.asStateFlow()

    private var cooldownJob: Job? = null

    init {
        startResendCooldown()
    }

    fun registerVerify(email: String, otp: String) {
        if (email.isEmpty() || otp.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        getData {
            when (val result = authRepository.registerVerifyOtp(email, otp)) {
                is DataResult.Success -> {
                    pendingRegistrationStore.clear(email)
                    DataResult.Success(VerifyOtpResult.RegisterSuccess(result.data))
                }
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

    fun resendOtp(email: String, isRegister: Boolean) {
        if (email.isBlank() || _resendCooldownSeconds.value > 0 ||
            _resendState.value is ResendOtpState.Loading
        ) {
            return
        }

        viewModelScope.launch {
            _resendState.value = ResendOtpState.Loading

            val result = if (isRegister) {
                val password = pendingRegistrationStore.passwordFor(email)
                if (password == null) {
                    DataResult.Error(
                        "Phiên đăng ký đã hết. Vui lòng quay lại và nhập lại mật khẩu."
                    )
                } else {
                    authRepository.registerSendOtp(email, password)
                }
            } else {
                authRepository.forgotPasswordSendOtp(email)
            }

            when (result) {
                is DataResult.Success -> {
                    _resendState.value = ResendOtpState.Success
                    startResendCooldown()
                }

                is DataResult.Error -> {
                    _resendState.value = ResendOtpState.Error(result.message)
                }
            }
        }
    }

    fun consumeResendResult() {
        _resendState.value = ResendOtpState.Idle
    }

    fun clearPendingRegistration(email: String) {
        pendingRegistrationStore.clear(email)
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        _resendCooldownSeconds.value = RESEND_COOLDOWN_SECONDS
        cooldownJob = viewModelScope.launch {
            while (_resendCooldownSeconds.value > 0) {
                delay(1_000L)
                _resendCooldownSeconds.value -= 1
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
