package com.pando.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.sse.SseManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.CurrentUserProfile
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.response.PostResponse
import com.pando.app.features.home.data.repository.ProfileRepository
import com.pando.app.features.home.data.store.PostFeedStore
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
    private val gson: Gson,
    private val profileRepository: ProfileRepository,
    private val userSession: UserSession,
    private val postFeedStore: PostFeedStore
) : ViewModel() {
    val connectionState = socketConnectionManager.connectionState

    private val _nsfwStatuses = MutableStateFlow<Map<UUID, NsfwStatus>>(emptyMap())
    val nsfwStatuses = _nsfwStatuses.asStateFlow()

    private val _uiEvents = Channel<MainEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    private var loadedProfileUserId: UUID? = null
    private var loadingProfileUserId: UUID? = null

    fun socketConnect() {
        socketConnectionManager.connect()
    }

    fun socketDisconnect() {
        socketConnectionManager.disconnect()
        postFeedStore.reset()
        _nsfwStatuses.value = emptyMap()
        loadedProfileUserId = null
        loadingProfileUserId = null
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

    fun loadCurrentUserProfile(userId: UUID) {
        val currentUser = userSession.getCurrentUser() ?: return
        if (currentUser.id != userId) return
        if (currentUser.profile != null) {
            loadedProfileUserId = userId
            return
        }
        if (loadedProfileUserId == userId) return
        if (loadingProfileUserId == userId) return

        loadingProfileUserId = userId

        viewModelScope.launch {
            when (val result = profileRepository.getProfile(userId)) {
                is DataResult.Success -> {
                    val profile = result.data.data
                    var profileApplied = false

                    userSession.updateCurrentUser { currentUser ->
                        if (currentUser.id != userId) {
                            currentUser
                        } else {
                            profileApplied = true
                            currentUser.copy(
                                profile = CurrentUserProfile(
                                    birthday = profile.birthday,
                                    gender = profile.gender,
                                    phoneNumber = profile.phoneNumber
                                )
                            )
                        }
                    }

                    if (profileApplied) {
                        loadedProfileUserId = userId
                    }
                }

                is DataResult.Error -> {
                    // Ghi log hoặc phát lỗi nếu cần
                }
            }

            if (loadingProfileUserId == userId) {
                loadingProfileUserId = null
            }
        }
    }
}

sealed interface MainEvent {
    data class DetectedNsfw(val post: PostResponse) : MainEvent
}
