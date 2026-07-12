package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserSearchResultDTO

data class SearchResponse(
    val total: Int,
    val items: List<UserSearchResultDTO>
)