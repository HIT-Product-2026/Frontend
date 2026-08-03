package com.pando.app.core.network.socket

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.state.SocketConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class SocketConnectionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SocketConnectionManager"
    }

    @Volatile
    private var isNetworkAvailable = false

    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null

    private val _connectionState =
        MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val hasInternet = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )

            val isValidated = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )

            val available = hasInternet && isValidated

            if (available && !isNetworkAvailable) {
                handleNetworkAvailable()
            } else if (!available && isNetworkAvailable) {
                handleNetworkLost()
            }
        }

        override fun onLost(network: Network) {
            if (isNetworkAvailable) {
                handleNetworkLost()
            }
        }
    }

    init {
        isNetworkAvailable = checkInitialNetworkAvailable()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun connect() {
        shouldStayConnected = true

        reconnectJob?.cancel()
        reconnectJob = null

        if (!isNetworkAvailable) {
            _connectionState.value = SocketConnectionState.Error("Không có kết nối Internet")
            return
        }

        connectInternal()
    }

    private fun connectInternal() {
        if (!shouldStayConnected || !isNetworkAvailable) return

        val currentState = _connectionState.value

        if (currentState == SocketConnectionState.Connected || currentState == SocketConnectionState.Connecting) {
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
            Stomp.ConnectionProvider.OKHTTP, SocketConstants.BASE_URL
        ).apply {
            withClientHeartbeat(10_000)
            withServerHeartbeat(10_000)
        }

        stompClient = client

        lifecycleDisposable = client.lifecycle().subscribe(
            lifecycle@{ event ->
                if (client !== stompClient) return@lifecycle

                when (event.type) {
                    LifecycleEvent.Type.OPENED -> {
                        Log.d(TAG, "STOMP connection OPENED")

                        reconnectAttempt = 0
                        reconnectJob?.cancel()
                        reconnectJob = null

                        _connectionState.value = SocketConnectionState.Connected
                    }

                    LifecycleEvent.Type.CLOSED -> {

                        Log.d(TAG, "STOMP connection CLOSED")

                        _connectionState.value = SocketConnectionState.Disconnected
                        handleConnectionLost(client)
                    }

                    LifecycleEvent.Type.ERROR -> {
                        val message = event.exception?.message ?: "STOMP connection ERROR"

                        Log.e(TAG, message, event.exception)
                        _connectionState.value = SocketConnectionState.Error(message)
                        handleConnectionLost(client)
                    }

                    LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                        Log.e(TAG, "Không nhận được heartbeat từ server")

                        _connectionState.value =
                            SocketConnectionState.Error("Mất heartbeat với server")
                        handleConnectionLost(client)
                    }
                }
            },
            lifecycleError@{ throwable ->
                if (client !== stompClient) return@lifecycleError

                val message = throwable.message ?: "Không thể theo dõi STOMP lifecycle"

                Log.e(TAG, message, throwable)
                _connectionState.value = SocketConnectionState.Error(message)
                handleConnectionLost(client)
            })

        val connectHeaders = listOf(StompHeader("Authorization", "Bearer $accessToken"))

        client.connect(connectHeaders)

        Log.d(TAG, "Đã gọi STOMP connect")
    }

    fun disconnect() {
        Log.d(TAG, "Disconnect STOMP")

        shouldStayConnected = false

        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
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

    private fun handleConnectionLost(client: StompClient) {
        if (client !== stompClient) return

        clearClient()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        if (!isNetworkAvailable) return
        if (reconnectJob?.isActive == true) return

        val attempt = reconnectAttempt
        reconnectAttempt++

        reconnectJob = scope.launch {
            val delayMillis = calculateBackoff(attempt)

            Log.d(TAG, "Reconnect lần ${attempt + 1} sau ${delayMillis}ms")

            delay(delayMillis.milliseconds)

            reconnectJob = null

            if (shouldStayConnected) {
                connectInternal()
            }
        }
    }

    fun clearClient() {
        lifecycleDisposable?.dispose()
        lifecycleDisposable = null

        stompClient?.disconnect()
        stompClient = null
    }

    private fun calculateBackoff(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtMost(5)
        val exponentialDelay = min(
            30_000L,
            1_000L * (1L shl safeAttempt)
        )

        val jitterRange = exponentialDelay / 5 // 20%
        val jitter = Random.nextLong(-jitterRange, jitterRange + 1)

        return (exponentialDelay + jitter).coerceAtLeast(1_000L)
    }

    private fun handleNetworkAvailable() {
        isNetworkAvailable = true

        if (!shouldStayConnected) return

        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0

        connectInternal()
    }

    private fun handleNetworkLost() {
        isNetworkAvailable = false

        reconnectJob?.cancel()
        reconnectJob = null

        clearClient()

        if (shouldStayConnected) {
            _connectionState.value = SocketConnectionState.Error("Không có kết nối Internet")
        }
    }

    private fun checkInitialNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}