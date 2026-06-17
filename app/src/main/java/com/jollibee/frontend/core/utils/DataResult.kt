package com.jollibee.frontend.core.utils

sealed class DataResult<out T> {
    data class Success<out T>(val data: T, val message: String? = null) : DataResult<T>()
    data class Error(val message: String, val code: Int? = null) : DataResult<Nothing>()
}