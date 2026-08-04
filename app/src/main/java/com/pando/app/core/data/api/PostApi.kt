package com.pando.app.core.data.api

import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.data.model.response.PostResponse
import com.pando.app.features.home.data.model.response.PostsResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface PostApi {
    @Multipart
    @POST(ApiConstants.Post.CREATE_POST)
    suspend fun doPost(
        @Query("longitude") longitude: Double?,
        @Query("latitude") latitude: Double?,
        @Part file: MultipartBody.Part,
        @Query("caption") caption: String?,
        @Query("type") type: TypePost
    ): Response<ApiResponse<PostResponse>>
    @GET(ApiConstants.Post.GET_POST)
    suspend fun getPosts(@Query("cursor") cursor: String?) : Response<ApiResponse<PostsResponse>>
    @GET(ApiConstants.Post.GET_POST_IMAGE)
    suspend fun getPostImage(@Path("post_id") postId: UUID) : Response<ApiResponse<String>>
    @DELETE(ApiConstants.Post.DELETE_POST)
    suspend fun deletePost(@Path("post_id") postId: UUID) : Response<ApiResponse<Void>>
}