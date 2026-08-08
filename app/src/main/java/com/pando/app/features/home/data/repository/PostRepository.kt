package com.pando.app.features.home.data.repository

import android.util.LruCache
import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.PostApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.response.PostsResponse
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val postApi: PostApi
) : BaseRepository() {
    private val postCache = LruCache<UUID, ApiResponse<String>>(MAX_CACHE_ENTRIES)

    suspend fun getPostImage(postId: UUID): DataResult<ApiResponse<String>> {
        postCache.get(postId)?.let { cachedImage ->
            return DataResult.Success(cachedImage)
        }

        return when (val result = safeApiCall { postApi.getPostImage(postId) }) {
            is DataResult.Success -> {
                postCache.put(postId, result.data)
                DataResult.Success(result.data)
            }

            is DataResult.Error -> result
        }
    }

    fun getCachedImage(postId: UUID): ApiResponse<String>? {
        return postCache.get(postId)
    }

    fun clearCache() {
        postCache.evictAll()
    }

    suspend fun getPosts(cursor: String?): DataResult<ApiResponse<PostsResponse>> {
        return safeApiCall {
            postApi.getPosts(cursor)
        }
    }

    suspend fun deletePost(postId: UUID): DataResult<ApiResponse<Void>> {
        return when (
            val result = safeApiCall {
                postApi.deletePost(postId)
            }
        ) {
            is DataResult.Success -> {
                postCache.remove(postId)
                result
            }

            is DataResult.Error -> result
        }
    }

    private companion object {
        private const val MAX_CACHE_ENTRIES = 64
    }
}
