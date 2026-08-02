package com.pando.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.sse.SseManager
import com.pando.app.features.home.data.model.response.PostResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val socketConnectionManager: SocketConnectionManager,
    private val sseManager: SseManager,
    private val gson: Gson
) : ViewModel() {
    val connectionState = socketConnectionManager.connectionState

    private val _uiEvents = Channel<MainEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun socketConnect() {
        socketConnectionManager.connect()
    }

    fun socketDisconnect() {
        socketConnectionManager.disconnect()
    }

    init {
        viewModelScope.launch {
            sseManager.events.collect { event ->
                when (event.type) {
                    "DETECT_NSFW" -> {
                        handleDetectedNsfw(event.data)
                    }
                }
            }
        }
    }

    private suspend fun handleDetectedNsfw(data: String) {
        val post = runCatching {
            gson.fromJson(data, PostResponse::class.java)
        }.getOrNull() ?: return

        _uiEvents.send(MainEvent.DetectedNsfw(post))
    }
}

sealed interface MainEvent {
    data class DetectedNsfw(val post: PostResponse) : MainEvent
}