package com.pando.app.features.home.data.repository

import com.pando.app.core.base.BaseRepository
import com.pando.app.core.data.api.LocationApi
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.response.LocationsResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class LocationRepository @Inject constructor(
    private val locationApi: LocationApi
) : BaseRepository() {
    suspend fun getProvince(latitude: Double, longitude: Double): DataResult<ApiResponse<String>> {
        return safeApiCall {
            locationApi.getProvince(latitude, longitude)
        }
    }

    suspend fun getFriendLocations() : DataResult<ApiResponse<LocationsResponse>> {
        return safeApiCall {
            locationApi.getFriends()
        }
    }
}