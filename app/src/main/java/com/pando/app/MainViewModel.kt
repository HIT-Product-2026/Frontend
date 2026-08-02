package com.pando.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.sse.SseManager
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.response.PostResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val socketConnectionManager: SocketConnectionManager,
    private val sseManager: SseManager,
    private val gson: Gson
) : ViewModel() {
    val connectionState = socketConnectionManager.connectionState

    private val _nsfwStatuses = MutableStateFlow<Map<UUID, NsfwStatus>>(emptyMap())
    val nsfwStatuses = _nsfwStatuses.asStateFlow()

    private val _uiEvents = Channel<MainEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun socketConnect() {
        socketConnectionManager.connect()
    }

    fun socketDisconnect() {
        socketConnectionManager.disconnect()
        _nsfwStatuses.value = emptyMap()
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

        val status = post.nsfw ?: return

        _nsfwStatuses.update { current ->
            current + (post.id to status)
        }

        if (status == NsfwStatus.TRUE) {
            _uiEvents.send(MainEvent.DetectedNsfw(post))
        }
    }
}

sealed interface MainEvent {
    data class DetectedNsfw(val post: PostResponse) : MainEvent
}