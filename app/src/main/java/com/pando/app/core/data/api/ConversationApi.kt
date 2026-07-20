package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.request.SendMessageRequest
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import com.pando.app.features.home.data.model.response.ConversationsResponse
import com.pando.app.features.home.data.model.response.MessagePageResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface ConversationApi {
    @GET(ApiConstants.Conversation.GET_CONVERSATIONS)
    suspend fun getConversations(): Response<ApiResponse<ConversationsResponse>>

    @GET(ApiConstants.Conversation.GET_CONVERSATION_MESSAGES)
    suspend fun getConversationMessages(
        @Path("conversation_id") conversationId: UUID,
        @Query("cursor") cursor: String?): Response<ApiResponse<MessagePageResponse>>

    @GET(ApiConstants.Message.GET_IMAGE_MESSAGE)
    suspend fun getImageMessage(@Path("message_id") messageId: UUID) : Response<ResponseBody>

    @POST(ApiConstants.Message.SEND_TEXT_MESSAGE)
    suspend fun sendTextMessage(@Body request: SendMessageRequest) : Response<ApiResponse<ChatMessageResponse>>

    @Multipart
    @POST(ApiConstants.Message.SEND_IMAGE_MESSAGE)
    suspend fun sendImageMessage(
        @Query("conversationId") conversationId: UUID,
        @Part file : MultipartBody.Part) : Response<ApiResponse<ChatMessageResponse>>
}