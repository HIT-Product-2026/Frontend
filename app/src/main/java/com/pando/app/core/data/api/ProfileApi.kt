package com.pando.app.core.data.api

import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.features.home.data.model.request.UpdateProfileRequest
import com.pando.app.features.home.data.model.response.ProfileResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.UUID

interface ProfileApi {
    @PUT(ApiConstants.Profile.UPDATE_PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest) : Response<ApiResponse<Void>>
    @GET(ApiConstants.Profile.GET_PROFILE)
    suspend fun getProfile(@Path("user_id") userId : UUID): Response<ApiResponse<ProfileResponse>>
}