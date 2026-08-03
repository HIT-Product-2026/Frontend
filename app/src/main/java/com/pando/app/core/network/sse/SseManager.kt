package com.pando.app.core.network.sse

import android.util.Log
import com.pando.app.core.network.api.ApiConstants
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.state.SseConnectionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }

    private var eventSource: EventSource? = null

    private var manuallyDisconnected = false

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
        if (eventSource != null) {
            return
        }

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

            Log.d(TAG, "SSE event: type=${event.type}, data=${event.data}")

            _events.tryEmit(event)
        }

        override fun onClosed(eventSource: EventSource) {
            Log.d(TAG, "SSE connection closed")

            clearEventSource(eventSource)

            _connectionState.value = SseConnectionState.Disconnected
        }

        override fun onFailure(
            eventSource: EventSource,
            t: Throwable?,
            response: Response?
        ) {
            clearEventSource(eventSource)

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
        }
    }

    @Synchronized
    private fun clearEventSource(currentEventSource: EventSource) {
        if (eventSource === currentEventSource) {
            eventSource = null
        }
    }

    @Synchronized
    fun disconnect() {
        manuallyDisconnected = true

        eventSource?.cancel()
        eventSource = null

        _connectionState.value = SseConnectionState.Disconnected
    }
}