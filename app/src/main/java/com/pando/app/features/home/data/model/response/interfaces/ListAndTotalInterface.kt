package com.pando.app.features.home.data.model.response.interfaces

interface ListAndTotalInterface<T> {
    val total: Int
    val items: List<T>
}