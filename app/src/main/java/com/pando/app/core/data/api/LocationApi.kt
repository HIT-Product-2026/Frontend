package com.pando.app.core.data.api

import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.features.home.data.model.response.LocationsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LocationApi {
    @GET(ApiConstants.Location.GET_LOCATION_PROVINCE)
    suspend fun getProvince(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ) : Response<ApiResponse<String>>

    @GET(ApiConstants.Location.GET_LOCATION_FRIENDS)
    suspend fun getFriends() : Response<ApiResponse<LocationsResponse>>
}