package com.pando.app.features.shared

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarRepository @Inject constructor (
    private val userApi: UserApi
): BaseRepository() {

    private val avatarCache = ConcurrentHashMap<UUID, ApiResponse<String>>()

    suspend fun getUserAvatar(userId: UUID): DataResult<ApiResponse<String>> {
        avatarCache[userId]?.let { cachedAvatar ->
            return DataResult.Success(cachedAvatar)
        }

        return when (val result = safeApiCall { userApi.getUserAvatar(userId) }) {
            is DataResult.Success -> {
                avatarCache[userId] = result.data
                DataResult.Success(result.data)
            }

            is DataResult.Error -> result
        }
    }

    fun getCachedAvatar(userId: UUID): ApiResponse<String>? {
        return avatarCache[userId]
    }

    fun clearCache() {
        avatarCache.clear()
    }
}