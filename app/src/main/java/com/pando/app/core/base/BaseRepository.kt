package com.pando.app.core.base

import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import okhttp3.ResponseBody
import retrofit2.Response

open class BaseRepository {
    protected suspend fun <T> safeApiCall(call: suspend () -> Response<ApiResponse<T>>): DataResult<ApiResponse<T>> {
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

    protected suspend fun safeFileCall(call: suspend () -> Response<ResponseBody>): DataResult<ByteArray> {
        return try {
            val response = call()

            if (!response.isSuccessful) {
                return DataResult.Error(
                    response.message().ifBlank {"HTTP Error: ${response.code()}" },
                    response.code()
                )
            }

            val body = response.body()
                ?: return DataResult.Error("Response body is null", response.code())

            body.use { responseBody ->
                val bytes = responseBody.byteStream().use { inputStream ->
                    inputStream.readBytes()
                }

                DataResult.Success(bytes)
            }
        } catch (e: Exception) {
            DataResult.Error(
                message = e.message ?: "Không thể tải file"
            )
        }
    }
}