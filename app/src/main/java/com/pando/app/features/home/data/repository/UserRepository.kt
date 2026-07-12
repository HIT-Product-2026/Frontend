package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.FriendshipApi
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import java.util.UUID
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val userApi: UserApi,
    private val friendshipApi: FriendshipApi
) : BaseRepository() {
    suspend fun getFriendList(): DataResult<ApiResponse<FriendListResponse>> {
        return safeApiCall {
            friendshipApi.getFriendList()
        }
    }

    suspend fun searchUsers(keyword: String): DataResult<ApiResponse<SearchResponse>> {
        return safeApiCall {
            friendshipApi.searchUser(keyword)
        }
    }
}