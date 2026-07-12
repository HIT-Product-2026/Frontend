package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.auth.data.model.request.FPResetPasswordRequest
import com.pando.app.features.auth.data.model.request.FPSendEmailRequest
import com.pando.app.features.auth.data.model.request.FPVerifyOtpRequest
import com.pando.app.features.auth.data.model.request.LoginRequest
import com.pando.app.features.auth.data.model.request.RegisterSendOtpRequest
import com.pando.app.features.auth.data.model.request.RegisterVerifyOtpRequest
import com.pando.app.features.auth.data.model.response.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface AuthApi {
    @POST(ApiConstants.Auth.LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST(ApiConstants.Auth.REGISTER_SEND_OTP)
    suspend fun registerSendOtp(@Body request: RegisterSendOtpRequest): Response<ApiResponse<Void>>

    @POST(ApiConstants.Auth.REGISTER_VERIFY_OTP)
    suspend fun registerVerifyOtp(@Body request: RegisterVerifyOtpRequest): Response<ApiResponse<Void>>

    @POST(ApiConstants.Auth.FORGOT_PASSWORD_SEND_OTP)
    suspend fun forgotPasswordSendOtp(@Body request: FPSendEmailRequest): Response<ApiResponse<Void>>

    @POST(ApiConstants.Auth.FORGOT_PASSWORD_VERIFY_OTP)
    suspend fun forgotPasswordVerifyOtp(@Body request: FPVerifyOtpRequest): Response<ApiResponse<Void>>

    @POST(ApiConstants.Auth.RESET_PASSWORD)
    suspend fun resetPassword(@Body request: FPResetPasswordRequest): Response<ApiResponse<Void>>
}