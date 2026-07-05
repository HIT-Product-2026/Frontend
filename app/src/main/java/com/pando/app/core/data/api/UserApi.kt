package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import retrofit2.Response
import retrofit2.http.PUT
import retrofit2.http.Query

interface UserApi {
    @PUT(ApiConstants.User.SEND_FCM_TOKEN)
    suspend fun sendFcmToken(@Query("fcm_token") fcmToken: String) : Response<ApiResponse<Void>>
}