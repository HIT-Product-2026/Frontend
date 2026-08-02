package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class LocationsResponse (
    override val items: List<LocationResponse>,
    override val total: Int
) : ListAndTotalInterface<LocationResponse>