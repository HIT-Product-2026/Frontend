package com.pando.app.core.network

object ApiConstants {
    const val BASE_URL = "http://47.129.173.245:8080"
    const val API_V1 = "/api/v1/"

    object Auth {
        const val LOGIN = "auth/login"
        const val REGISTER_SEND_OTP = "auth/register/send-otp"
        const val REGISTER_VERIFY_OTP = "auth/register/verify-otp"
        const val FORGOT_PASSWORD_SEND_OTP = "auth/forgot-password/send-otp"
        const val FORGOT_PASSWORD_VERIFY_OTP = "auth/forgot-password/verify-otp"
        const val RESET_PASSWORD = "auth/forgot-password/reset"
    }

    object User {
        const val SEND_FCM_TOKEN = "user/me/fcm-token"
    }
}
