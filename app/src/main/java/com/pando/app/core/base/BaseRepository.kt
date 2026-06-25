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
                if (body != null) {
                    DataResult.Success(body)
                } else {
                    DataResult.Error("Response body is null", response.code())
                }
            } else {
                DataResult.Error(
                    message = response.message().ifBlank { "HTTP Error: ${response.code()}" },
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}