package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.ConversationApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.request.SendMessageRequest
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import com.pando.app.features.home.data.model.response.ConversationsResponse
import com.pando.app.features.home.data.model.response.MessagePageResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ConversationRepository @Inject constructor(
    private val conversationApi: ConversationApi
) : BaseRepository() {

//    private val imageCache = ConcurrentHashMap<UUID, ApiResponse<String>>()

    suspend fun getConversation(): DataResult<ApiResponse<ConversationsResponse>> {
        return safeApiCall {
            conversationApi.getConversations()
        }
    }

    suspend fun getConversationMessages(
        conversationId: UUID,
        cursor: String?
    ): DataResult<ApiResponse<MessagePageResponse>> {
        return safeApiCall {
            conversationApi.getConversationMessages(conversationId, cursor)
        }
    }

//    suspend fun getImageMessage(messageId: UUID): DataResult<ApiResponse<String>> {
//        imageCache[messageId]?.let { cachedAvatar ->
//            return DataResult.Success(cachedAvatar)
//        }
//
//        return when (val result = safeApiCall { conversationApi.getImageMessage(messageId) }) {
//            is DataResult.Success -> {
//                imageCache[messageId] = result.data
//                DataResult.Success(result.data)
//            }
//
//            is DataResult.Error -> result
//        }
//    }
//
//    fun getCachedImage(messageId: UUID): ApiResponse<String>? {
//        return imageCache[messageId]
//    }
//
//    fun clearCache() {
//        imageCache.clear()
//    }

    suspend fun sendTextMessage(
        conversationId: UUID,
        content: String
    ): DataResult<ApiResponse<ChatMessageResponse>> {
        return safeApiCall {
            conversationApi.sendTextMessage(SendMessageRequest(conversationId, content))
        }
    }

    suspend fun sendImageMessage(
        conversationId: UUID,
        image: ByteArray
    ): DataResult<ApiResponse<ChatMessageResponse>> {
        val mediaType = "image/*".toMediaTypeOrNull()

        val requestBody = RequestBody.create(mediaType, image)

        val body = MultipartBody.Part.createFormData(
            "file",
            "photoFile.jpg",
            requestBody
        )

        return safeApiCall {
            conversationApi.sendImageMessage(conversationId, body)
        }
    }
}