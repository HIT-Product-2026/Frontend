package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.PostApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.response.PostsResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class PostRepository @Inject constructor (
    private val postApi: PostApi
): BaseRepository() {
    private val postCache = ConcurrentHashMap<UUID, ApiResponse<String>>()

    suspend fun getPostImage(postId: UUID): DataResult<ApiResponse<String>> {
        postCache[postId]?.let { cachedAvatar ->
            return DataResult.Success(cachedAvatar)
        }

        return when (val result = safeApiCall { postApi.getPostImage(postId) }) {
            is DataResult.Success -> {
                postCache[postId] = result.data
                DataResult.Success(result.data)
            }

            is DataResult.Error -> result
        }
    }

    fun getCachedImage(userId: UUID): ApiResponse<String>? {
        return postCache[userId]
    }

    fun clearCache() {
        postCache.clear()
    }

    suspend fun getPosts(cursor: String?) : DataResult<ApiResponse<PostsResponse>> {
        return safeApiCall {
            postApi.getPosts(cursor)
        }
    }
}