package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.ConversationApi
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.response.ConversationsResponse
import javax.inject.Inject

class ConversationRepository @Inject constructor(
    private val conversationApi: ConversationApi
): BaseRepository() {

    suspend fun getConversation(): DataResult<ApiResponse<ConversationsResponse>> {
        return safeApiCall {
            conversationApi.getConversations()
        }
    }
}