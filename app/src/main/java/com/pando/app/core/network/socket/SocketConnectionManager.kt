package com.pando.app.core.network.socket

import android.util.Log
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.state.SocketConnectionState
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketConnectionManager @Inject constructor(
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SocketConnectionManager"
    }

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null

    private val _connectionState =
        MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    fun connect() {
        val currentState = _connectionState.value

        if (currentState == SocketConnectionState.Connected ||
            currentState == SocketConnectionState.Connecting
        ) {
            Log.d(TAG, "Socket đang kết nối hoặc đã kết nối")
            return
        }

        val accessToken = tokenManager.getAccessToken()

        if (accessToken.isNullOrBlank()) {
            _connectionState.value = SocketConnectionState.Error("Không tìm thấy access token")
            return
        }

        _connectionState.value = SocketConnectionState.Connecting

        clearClient()

        val client = Stomp.over(
            Stomp.ConnectionProvider.OKHTTP,
            SocketConstants.BASE_URL
        ).apply {
            withClientHeartbeat(10_000)
            withServerHeartbeat(10_000)
        }

        stompClient = client

        lifecycleDisposable = client.lifecycle()
            .subscribe(
                { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> {
                            Log.d(TAG, "STOMP connection OPENED")

                            _connectionState.value = SocketConnectionState.Connected
                        }

                        LifecycleEvent.Type.CLOSED -> {

                            Log.d(TAG, "STOMP connection CLOSED")

                            _connectionState.value = SocketConnectionState.Disconnected
                        }

                        LifecycleEvent.Type.ERROR -> {
                            val message = event.exception?.message ?: "STOMP connection ERROR"

                            Log.e(TAG, message, event.exception)
                            _connectionState.value = SocketConnectionState.Error(message)
                        }

                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            Log.e(TAG, "Không nhận được heartbeat từ server")

                            _connectionState.value =
                                SocketConnectionState.Error("Mất heartbeat với server")
                        }
                    }
                },
                { throwable ->
                    val message = throwable.message ?: "Không thể theo dõi STOMP lifecycle"

                    Log.e(TAG, message, throwable)
                    _connectionState.value = SocketConnectionState.Error(message)
                }
            )

        val connectHeaders = listOf(StompHeader("Authorization", "Bearer $accessToken"))

        client.connect(connectHeaders)

        Log.d(TAG, "Đã gọi STOMP connect")
    }

    fun disconnect() {
        Log.d(TAG, "Disconnect STOMP")

        clearClient()

        _connectionState.value = SocketConnectionState.Disconnected
    }

    fun getConnectedClient(): StompClient? {
        return if (_connectionState.value == SocketConnectionState.Connected) {
            stompClient
        } else {
            null
        }
    }

    fun clearClient() {
        lifecycleDisposable?.dispose()
        lifecycleDisposable = null

        stompClient?.disconnect()
        stompClient = null
    }
}