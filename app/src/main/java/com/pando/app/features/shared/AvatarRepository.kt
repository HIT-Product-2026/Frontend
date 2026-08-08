package com.pando.app.features.shared

import android.util.LruCache
import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarRepository @Inject constructor (
    private val userApi: UserApi
): BaseRepository() {

    private val avatarCache = LruCache<UUID, ApiResponse<String>>(MAX_CACHE_ENTRIES)

    suspend fun getUserAvatar(userId: UUID): DataResult<ApiResponse<String>> {
        avatarCache.get(userId)?.let { cachedAvatar ->
            return DataResult.Success(cachedAvatar)
        }

        return when (val result = safeApiCall { userApi.getUserAvatar(userId) }) {
            is DataResult.Success -> {
                avatarCache.put(userId, result.data)
                DataResult.Success(result.data)
            }

            is DataResult.Error -> result
        }
    }

    fun getCachedAvatar(userId: UUID): ApiResponse<String>? {
        return avatarCache.get(userId)
    }

    fun clearCache() {
        avatarCache.evictAll()
    }

    private companion object {
        private const val MAX_CACHE_ENTRIES = 128
    }
}
