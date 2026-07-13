package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.FriendshipApi
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.dto.FriendshipDto
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.RequestedUsersResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import java.util.UUID
import javax.inject.Inject

class FriendshipRepository @Inject constructor(
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

    suspend fun getSentRequestedUsers(): DataResult<ApiResponse<RequestedUsersResponse>> {
        return safeApiCall {
            friendshipApi.getSentRequestedUsers()
        }
    }

    suspend fun getReceivedRequestedUsers(): DataResult<ApiResponse<RequestedUsersResponse>> {
        return safeApiCall {
            friendshipApi.getReceivedRequestedUsers()
        }
    }

    suspend fun requestFriend(userId: UUID): DataResult<ApiResponse<FriendshipDto>> {
        return safeApiCall {
            friendshipApi.requestFriend(userId)
        }
    }

    suspend fun acceptFriend(friendshipsId: UUID): DataResult<ApiResponse<FriendshipDto>> {
        return safeApiCall {
            friendshipApi.acceptFriend(friendshipsId)
        }
    }

    suspend fun rejectFriend(friendshipsId: UUID): DataResult<ApiResponse<FriendshipDto>> {
        return safeApiCall {
            friendshipApi.rejectFriend(friendshipsId)
        }
    }

    suspend fun unfriend(friendId: UUID) : DataResult<ApiResponse<FriendshipDto>> {
        return safeApiCall {
            friendshipApi.unfriend(friendId)
        }
    }
}