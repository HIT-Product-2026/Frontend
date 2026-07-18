package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.request.UpdateProfileRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT

interface ProfileApi {
    @PUT(ApiConstants.Profile.UPDATE_PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest) : Response<ApiResponse<Void>>
}