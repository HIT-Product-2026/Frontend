package com.pando.app.core.data.api

import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface UserApi {
    @PUT(ApiConstants.User.SEND_FCM_TOKEN)
    suspend fun sendFcmToken(@Query("fcm_token") fcmToken: String) : Response<ApiResponse<Void>>

    @GET(ApiConstants.User.GET_USER_AVATAR)
    suspend fun getUserAvatar(@Path("user_id") userid : UUID) : Response<ApiResponse<String>>
    @PUT(ApiConstants.User.UPDATE_DISPLAY_NAME)
    suspend fun updateDisplayName(@Query("displayName") displayName: String) : Response<ApiResponse<Void>>
    @Multipart
    @POST(ApiConstants.User.UPDATE_AVATAR)
    suspend fun updateAvatar(@Part file: MultipartBody.Part) : Response<ApiResponse<Void>>
    @PUT(ApiConstants.User.UPDATE_USER_MODE)
    suspend fun updateUserMode(@Query("mode") mode: UserMode) : Response<ApiResponse<Void>>
}