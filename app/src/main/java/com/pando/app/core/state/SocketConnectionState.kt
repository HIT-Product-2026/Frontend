package com.pando.app.core.state

sealed interface SocketConnectionState {
    data object Disconnected : SocketConnectionState
    data object Connecting : SocketConnectionState
    data object Connected : SocketConnectionState
    data class Error(val message: String) : SocketConnectionState
}