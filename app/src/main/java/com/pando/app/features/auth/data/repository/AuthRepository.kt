package com.pando.app.features.auth.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.api.AuthApi
import com.pando.app.features.auth.data.model.request.FPResetPasswordRequest
import com.pando.app.features.auth.data.model.request.FPSendEmailRequest
import com.pando.app.features.auth.data.model.request.FPVerifyOtpRequest
import com.pando.app.features.auth.data.model.request.LoginRequest
import com.pando.app.features.auth.data.model.request.RegisterSendOtpRequest
import com.pando.app.features.auth.data.model.request.RegisterVerifyOtpRequest
import com.pando.app.features.auth.data.model.response.LoginResponse
import jakarta.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) : BaseRepository() {
    suspend fun login(email: String, password: String): DataResult<ApiResponse<LoginResponse>> {
        return safeApiCall {
            authApi.login(LoginRequest(email, password))
        }
    }

    suspend fun registerSendOtp(email: String, password: String): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            authApi.registerSendOtp(RegisterSendOtpRequest(email, password))
        }
    }

    suspend fun registerVerifyOtp(email: String, otp: String): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            authApi.registerVerifyOtp(RegisterVerifyOtpRequest(email, otp))
        }
    }

    suspend fun forgotPasswordSendOtp(email: String): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            authApi.forgotPasswordSendOtp(FPSendEmailRequest(email))
        }
    }

    suspend fun forgotPasswordVerifyOtp(email: String, otp: String): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            authApi.forgotPasswordVerifyOtp(FPVerifyOtpRequest(email, otp))
        }
    }

    suspend fun resetPassword(email: String, password: String): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            authApi.resetPassword(FPResetPasswordRequest(email, password))
        }
    }
}