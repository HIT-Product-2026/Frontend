package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.PostApi
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.data.model.response.PostResponse
import jakarta.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MediaRepository @Inject constructor(
    private val postApi: PostApi,
    private val userApi: UserApi
) : BaseRepository() {
    suspend fun sendPost(
        caption: String?,
        longitude: Double?,
        latitude: Double?,
        mediaFile: File,
        type: TypePost
    ): DataResult<ApiResponse<PostResponse>> {
        val mimeType = when (type) {
            TypePost.IMAGE -> "image/jpeg"
            TypePost.VIDEO -> "video/mp4"
        }

        val requestFile = mediaFile.asRequestBody(
            mimeType.toMediaTypeOrNull()
        )

        val body = MultipartBody.Part.createFormData(
            "file",
            mediaFile.name,
            requestFile
        )

        return safeApiCall {
            postApi.doPost(longitude, latitude, body, caption, type)
        }
    }

    suspend fun sendAvatar(
        photoFile: MultipartBody.Part
    ): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            userApi.updateAvatar(photoFile)
        }
    }
}