package com.jollibee.frontend.core.network

data class ApiResponse<T>(
    val code: Int,
    val success: Boolean,
    val message: String,
    val data: T
)
