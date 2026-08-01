package com.pando.app.core.network.api

data class ApiResponse<T>(
    val code: Int,
    val success: Boolean,
    val message: String,
    val data: T
)
