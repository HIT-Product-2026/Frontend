package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.UserApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val userApi: UserApi
) : BaseRepository() {
    suspend fun updateDisplayName(displayName: String) : DataResult<ApiResponse<Void>> {
        return safeApiCall {
            userApi.updateDisplayName(displayName)
        }
    }

    suspend fun updateUserMode(mode: UserMode): DataResult<ApiResponse<Void>> {
        return safeApiCall {
            userApi.updateUserMode(mode)
        }
    }
}
