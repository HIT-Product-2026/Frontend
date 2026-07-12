package com.pando.app.core.data.api

import com.pando.app.core.network.ApiConstants
import com.pando.app.core.network.ApiResponse
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface FriendshipApi {
    @GET(ApiConstants.FriendShip.GET_FRIEND_LIST)
    suspend fun getFriendList() : Response<ApiResponse<FriendListResponse>>
    @GET(ApiConstants.FriendShip.SEARCH_USER)
    suspend fun searchUser(@Query("keyword") keyword: String) : Response<ApiResponse<SearchResponse>>
}