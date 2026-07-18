package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.ProfileApi
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.Gender
import com.pando.app.features.home.data.model.request.UpdateProfileRequest
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val profileApi: ProfileApi
) : BaseRepository() {
    suspend fun updateProfile(
        birthday: String,
        gender: Gender,
        phoneNumber: String
    ) : DataResult<ApiResponse<Void>> {
        return safeApiCall {
            profileApi.updateProfile(UpdateProfileRequest(birthday, gender, phoneNumber))
        }
    }
}