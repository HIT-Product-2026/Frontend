package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.response.FriendListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.UUID

interface UserApi {
    @PUT(ApiConstants.User.SEND_FCM_TOKEN)
    suspend fun sendFcmToken(@Query("fcm_token") fcmToken: String) : Response<ApiResponse<Void>>
    @Streaming
    @GET(ApiConstants.User.GET_USER_AVATAR)
    suspend fun getUserAvatar(@Path("user_id") userid : UUID) : Response<ResponseBody>
}