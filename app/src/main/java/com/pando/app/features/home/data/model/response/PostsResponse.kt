package com.pando.app.features.home.data.model.response

import com.pando.app.features.home.data.model.response.interfaces.ListAndTotalInterface

data class PostsResponse (
    override val total: Int,
    override val items: List<PostResponse>,
    val cursor: String?
) : ListAndTotalInterface<PostResponse>