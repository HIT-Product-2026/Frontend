package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.response.ConversationsResponse
import retrofit2.Response
import retrofit2.http.GET

interface ConversationApi {
    @GET(ApiConstants.Conversation.GET_CONVERSATIONS)
    suspend fun getConversations(): Response<ApiResponse<ConversationsResponse>>
}