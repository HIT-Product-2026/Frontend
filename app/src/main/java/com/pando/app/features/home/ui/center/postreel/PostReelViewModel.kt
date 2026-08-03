package com.pando.app.features.home.ui.center.postreel

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwViewDecision
import com.pando.app.features.home.data.model.entity.enumEntity.PostModeLocation
import com.pando.app.features.home.data.model.response.PostsResponse
import com.pando.app.features.home.data.repository.LocationRepository
import com.pando.app.features.home.data.repository.PostRepository
import com.pando.app.features.home.data.socket.MessagesSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PostReelViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val messagesSocket: MessagesSocket,
    private val socketConnectionManager: SocketConnectionManager,
    private val locationRepository: LocationRepository
) : BaseVM<ApiResponse<PostsResponse>>() {
    val connectionState = socketConnectionManager.connectionState
    private var isLoading = false

    private val _images = MutableStateFlow<Map<UUID, String>>(emptyMap())
    val images = _images.asStateFlow()
    private val _provinceNames = MutableStateFlow<Map<UUID, String>>(emptyMap())
    val provinceNames = _provinceNames.asStateFlow()
    private val _deletePostState = MutableStateFlow<UiState<UUID>>(UiState.Idle)

    val deletePostState = _deletePostState.asStateFlow()

    private val loadingImageIds = mutableSetOf<UUID>()
    private val loadingProvinceIds = mutableSetOf<UUID>()

    fun loadPost(postId: UUID, longitude: Double?, latitude: Double?) {
        loadPostImage(postId)

        if (latitude != null && longitude != null) {
            loadProvince(postId, latitude, longitude)
        }
    }

    private val _nsfwDecisions = MutableStateFlow<Map<UUID, NsfwViewDecision>>(emptyMap())
    val nsfwDecisions = _nsfwDecisions.asStateFlow()

    fun loadPostImage(postId: UUID) {
        if (_images.value.containsKey(postId)) return
        if (!loadingImageIds.add(postId)) return

        viewModelScope.launch {
            when (val result = postRepository.getPostImage(postId)) {
                is DataResult.Success -> {
                    _images.update { current ->
                        current + (postId to result.data.data)
                    }
                }

                is DataResult.Error -> {
                    // Emit event
                }
            }

            loadingImageIds.remove(postId)
        }
    }

    private fun loadProvince(postId: UUID, latitude: Double, longitude: Double) {
        if (_provinceNames.value.containsKey(postId)) return
        if (!loadingProvinceIds.add(postId)) return

        viewModelScope.launch {
            try {
                when (val result = locationRepository.getProvince(latitude, longitude)) {
                    is DataResult.Success -> {
                        _provinceNames.update { current ->
                            current + (postId to result.data.data)
                        }
                    }

                    is DataResult.Error -> {}
                }
            } finally {
                loadingProvinceIds.remove(postId)
            }
        }
    }

    fun getPosts() {
        if (isLoading) return

        if (DataPostReelItem.hasLoadedFirstPage &&
            DataPostReelItem.nextCursor == null
        ) {
            return
        }

        isLoading = true
        val requestedCursor = DataPostReelItem.nextCursor

        getData {
            val result = postRepository.getPosts(requestedCursor)

            if (result is DataResult.Success) {
                val response = result.data.data

                DataPostReelItem.total = DataPostReelItem.total?.plus(response.total)

                val existingIds = DataPostReelItem.data
                    .mapTo(hashSetOf()) { it.id }

                val newPosts = response.items
                    .filter { existingIds.add(it.id) }
                    .map { post ->
                        when (post.modeLocation) {
                            PostModeLocation.PUBLIC -> {
                                PostReelItemModel(
                                    id = post.id,
                                    user = post.user,
                                    caption = post.caption,
                                    latitude = post.latitude,
                                    longitude = post.longitude,
                                    modeLocation = post.modeLocation,
                                    nsfw = post.nsfw,
                                    conversationId = post.conversation?.id,
                                    createdAt = post.createAt?.toLocalDateTime()
                                )
                            }

                            PostModeLocation.PRIVATE -> {
                                PostReelItemModel(
                                    id = post.id,
                                    user = post.user,
                                    caption = post.caption,
                                    latitude = null,
                                    longitude = null,
                                    nsfw = post.nsfw,
                                    modeLocation = post.modeLocation,
                                    conversationId = post.conversation?.id,
                                    createdAt = post.createAt?.toLocalDateTime()
                                )
                            }
                        }
                    }

                DataPostReelItem.data.addAll(newPosts)
                DataPostReelItem.hasLoadedFirstPage = true
                DataPostReelItem.nextCursor = result.data.data.cursor
            }

            isLoading = false

            result
        }
    }

    fun sendImagePost(conversationId: UUID, postImageUrl: String) {
        messagesSocket.sendImageMessage(conversationId, postImageUrl)
    }

    fun sendMessage(conversationId: UUID, message: String) {
        messagesSocket.sendMessage(conversationId, message)
    }

    fun updateNsfwDecision(postId: UUID, decision: NsfwViewDecision) {
        _nsfwDecisions.update { current ->
            current + (postId to decision)
        }
    }

    fun deletePost(postId: UUID) {
        if (_deletePostState.value is UiState.Loading) return

        viewModelScope.launch {
            _deletePostState.value = UiState.Loading

            when (val result = postRepository.deletePost(postId)) {
                is DataResult.Success -> {
                    _images.update { it - postId }
                    _provinceNames.update { it - postId }
                    _nsfwDecisions.update { it - postId }

                    _deletePostState.value = UiState.Success(postId)
                }

                is DataResult.Error -> {
                    _deletePostState.value =
                        UiState.Error(result.message)
                }
            }
        }
    }

    fun clearDeletePostState() {
        _deletePostState.value = UiState.Idle
    }
}