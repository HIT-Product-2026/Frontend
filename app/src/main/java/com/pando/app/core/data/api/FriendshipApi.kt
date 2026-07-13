package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.dto.FriendshipDto
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.RequestedUsersResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface FriendshipApi {
    @GET(ApiConstants.FriendShip.GET_FRIEND_LIST)
    suspend fun getFriendList() : Response<ApiResponse<FriendListResponse>>
    @GET(ApiConstants.FriendShip.SEARCH_USER)
    suspend fun searchUser(@Query("keyword") keyword: String) : Response<ApiResponse<SearchResponse>>
    @GET(ApiConstants.FriendShip.GET_SENT_REQUESTED_USERS)
    suspend fun getSentRequestedUsers() : Response<ApiResponse<RequestedUsersResponse>>
    @GET(ApiConstants.FriendShip.GET_RECEIVED_REQUESTED_USERS)
    suspend fun getReceivedRequestedUsers() : Response<ApiResponse<RequestedUsersResponse>>
    @POST(ApiConstants.FriendShip.REQUEST_FRIEND)
    suspend fun requestFriend(@Query("receiverId") receiverId: UUID) : Response<ApiResponse<FriendshipDto>>
    @POST(ApiConstants.FriendShip.ACCEPT_FRIEND)
    suspend fun acceptFriend(@Path("friendships_id") friendshipsId: UUID): Response<ApiResponse<FriendshipDto>>
    @POST(ApiConstants.FriendShip.REJECT_FRIEND)
    suspend fun rejectFriend(@Path("friendships_id") friendshipsId: UUID): Response<ApiResponse<FriendshipDto>>
    @POST(ApiConstants.FriendShip.UNFRIEND)
    suspend fun unfriend(@Path("friendId") friendId: UUID) : Response<ApiResponse<FriendshipDto>>
}