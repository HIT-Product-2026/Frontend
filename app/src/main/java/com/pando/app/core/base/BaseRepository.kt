package com.pando.app.core.base

import com.google.gson.Gson
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response

open class BaseRepository {
    private val gson by lazy(::Gson)

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
                val message = response.errorBody()
                    ?.use { it.string() }
                    ?.takeIf(String::isNotBlank)
                    ?.let { errorBody ->
                        runCatching {
                            gson.fromJson(errorBody, ApiResponse::class.java)?.message
                        }.getOrNull()
                    }
                    ?.takeIf(String::isNotBlank)
                    ?: response.message().takeIf(String::isNotBlank)
                    ?: "HTTP ${response.code()}"
                DataResult.Error(
                    message = message,
                    code = response.code()
                )
            }
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DataResult.Error(
                message = e.message ?: "Không thể tải file"
            )
        }
    }
}
