package com.pando.app.core.network.sse

import android.util.Log
import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.state.SseConnectionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseManager @Inject constructor(
    @param:SseOkHttpClient
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SseManager"
        private const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
    }

    private var eventSource: EventSource? = null

    private var manuallyDisconnected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val reconnectScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val _connectionState =
        MutableStateFlow<SseConnectionState>(SseConnectionState.Disconnected)

    val connectionState = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<SseEventData>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = _events.asSharedFlow()

    @Synchronized
    fun connect() {
        manuallyDisconnected = false

        if (eventSource != null) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null
        openConnectionLocked()
    }

    /** Must be called while holding this manager's monitor. */
    private fun openConnectionLocked() {
        val accessToken = tokenManager.getAccessToken()

        if (accessToken.isNullOrBlank()) {
            _connectionState.value = SseConnectionState.Error("Không tìm thấy access token")
            return
        }

        manuallyDisconnected = false
        _connectionState.value = SseConnectionState.Connecting

        val url = "${ApiConstants.BASE_URL + ApiConstants.API_V2}sse/subscribe"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept","text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val factory = EventSources.createFactory(okHttpClient)

        eventSource = factory.newEventSource(request, eventSourceListener)
    }

    private val eventSourceListener = object : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            synchronized(this@SseManager) {
                if (this@SseManager.eventSource !== eventSource || manuallyDisconnected) return

                reconnectAttempt = 0
                reconnectJob?.cancel()
                reconnectJob = null
            }

            Log.d(TAG, "SSE connected: ${response.code}")

            _connectionState.value = SseConnectionState.Connected
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            val event = SseEventData(
                id = id,
                type = type ?: "message",
                data = data
            )

            _events.tryEmit(event)
        }

        override fun onClosed(eventSource: EventSource) {
            Log.d(TAG, "SSE connection closed")

            if (!clearEventSource(eventSource)) return

            _connectionState.value = SseConnectionState.Disconnected
            scheduleReconnect()
        }

        override fun onFailure(
            eventSource: EventSource,
            t: Throwable?,
            response: Response?
        ) {
            if (!clearEventSource(eventSource)) return

            if (manuallyDisconnected) {
                _connectionState.value = SseConnectionState.Disconnected
                return
            }

            val message = when {
                response?.code == 401 ->
                    "Access token không hợp lệ hoặc đã hết hạn"

                response != null ->
                    "SSE lỗi HTTP ${response.code}"

                t != null ->
                    t.message ?: "Mất kết nối SSE"

                else ->
                    "Không thể kết nối SSE"
            }

            Log.e(TAG, message, t)

            _connectionState.value = SseConnectionState.Error(message)

            // A 401/403 needs a new authenticated session; retrying the same
            // request forever would only create a reconnect loop and spam BE.
            if (response?.code != 401 && response?.code != 403) {
                scheduleReconnect()
            }
        }
    }

    @Synchronized
    private fun clearEventSource(currentEventSource: EventSource): Boolean {
        if (eventSource === currentEventSource) {
            eventSource = null
            return true
        }

        return false
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (manuallyDisconnected || eventSource != null) return
        if (reconnectJob?.isActive == true) return

        val attempt = reconnectAttempt
        reconnectAttempt++

        reconnectJob = reconnectScope.launch {
            val delayMillis = reconnectDelayMillis(attempt)
            Log.d(TAG, "SSE reconnect lần ${attempt + 1} sau ${delayMillis}ms")

            delay(delayMillis)

            synchronized(this@SseManager) {
                reconnectJob = null

                if (manuallyDisconnected || eventSource != null) return@synchronized

                openConnectionLocked()
            }
        }
    }

    private fun reconnectDelayMillis(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtMost(5)
        return minOf(
            MAX_RECONNECT_DELAY_MILLIS,
            INITIAL_RECONNECT_DELAY_MILLIS * (1L shl safeAttempt)
        )
    }

    @Synchronized
    fun disconnect() {
        manuallyDisconnected = true
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null

        eventSource?.cancel()
        eventSource = null

        _connectionState.value = SseConnectionState.Disconnected
    }
}
