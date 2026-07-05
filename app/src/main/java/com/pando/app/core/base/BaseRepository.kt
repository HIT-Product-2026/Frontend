package com.pando.app.core.base

import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import retrofit2.Response

open class BaseRepository {
    protected suspend fun <T> safeApiCall( call: suspend () -> Response<ApiResponse<T>> ): DataResult<ApiResponse<T>> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    DataResult.Success(body)
                } else {
                    DataResult.Error(body?.message ?: "Response body is null")
                }
            } else {
                val body = response.errorBody()?.string()
                val apiResponse = com.google.gson.Gson().fromJson(body, ApiResponse::class.java)
                val message: String = apiResponse.message
                DataResult.Error(
                    message = message,
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}