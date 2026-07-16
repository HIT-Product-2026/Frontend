package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.dto.UserSearchResultDTO

data class SearchResponse(
    override val total: Int,
    override val items: List<UserSearchResultDTO>
): ListAndTotalInterface<UserSearchResultDTO>