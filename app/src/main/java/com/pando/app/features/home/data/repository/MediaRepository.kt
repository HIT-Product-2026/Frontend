package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.PostApi
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
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
        photoFile: File
    ): DataResult<ApiResponse<PostResponse>> {
        val requestFile = photoFile.asRequestBody("image/*".toMediaTypeOrNull())

        val body = MultipartBody.Part.createFormData(
            "file",
            photoFile.name,
            requestFile
        )

        return safeApiCall {
            postApi.doPost(longitude, latitude, body, caption)
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