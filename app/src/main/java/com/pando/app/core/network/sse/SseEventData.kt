package com.pando.app.core.network.sse

data class SseEventData(
    val id: String?,
    val type: String,
    val data: String
)