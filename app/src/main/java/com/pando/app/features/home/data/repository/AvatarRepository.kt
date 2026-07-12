package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.utils.DataResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarRepository @Inject constructor (
    private val userApi: UserApi
): BaseRepository() {

    private val avatarCache = ConcurrentHashMap<UUID, ByteArray>()

    suspend fun getUserAvatar(userId: UUID): DataResult<ByteArray> {
        avatarCache[userId]?.let { cachedAvatar ->
            return DataResult.Success(cachedAvatar)
        }

        return when (val result = safeFileCall { userApi.getUserAvatar(userId) }) {
            is DataResult.Success -> {
                avatarCache[userId] = result.data
                DataResult.Success(result.data)
            }

            is DataResult.Error -> result
        }
    }

    fun getCachedAvatar(userId: UUID): ByteArray? {
        return avatarCache[userId]
    }

    fun clearCache() {
        avatarCache.clear()
    }
}