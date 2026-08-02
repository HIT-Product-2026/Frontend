package com.pando.app.core.state

sealed interface SseConnectionState {
    data object Disconnected : SseConnectionState
    data object Connecting : SseConnectionState
    data object Connected : SseConnectionState
    data class Error(val message: String) : SseConnectionState
}