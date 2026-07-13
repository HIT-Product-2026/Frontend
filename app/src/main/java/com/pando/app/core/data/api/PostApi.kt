package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.response.PostResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface PostApi {
    @Multipart
    @POST(ApiConstants.Post.CREATE_POST)
    suspend fun doPost(
        @Query("longitude") longitude: Double?,
        @Query("latitude") latitude: Double?,
        @Part file: MultipartBody.Part,
        @Query("caption") caption: String?): Response<ApiResponse<PostResponse>>
}