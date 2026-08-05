package com.pando.app.features.home.ui.center.postreel

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwViewDecision
import com.pando.app.features.home.data.model.entity.enumEntity.PostModeLocation
import com.pando.app.features.home.data.model.response.PostsResponse
import com.pando.app.features.home.data.repository.PostRepository
import com.pando.app.features.home.data.socket.MessagesSocket
import com.pando.app.features.home.data.store.PostFeedStore
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
    private val postFeedStore: PostFeedStore
) : BaseVM<ApiResponse<PostsResponse>>() {
    val connectionState = socketConnectionManager.connectionState
    val posts = postFeedStore.posts
    private var isLoading = false

    private val _deletePostState = MutableStateFlow<UiState<UUID>>(UiState.Idle)

    val deletePostState = _deletePostState.asStateFlow()

    private val _nsfwDecisions = MutableStateFlow<Map<UUID, NsfwViewDecision>>(emptyMap())
    val nsfwDecisions = _nsfwDecisions.asStateFlow()

    fun getPosts() {
        if (isLoading) return

        if (postFeedStore.hasReachedEnd()) {
            return
        }

        isLoading = true
        val requestedCursor = postFeedStore.getNextCursor()

        getData {
            val result = postRepository.getPosts(requestedCursor)

            if (result is DataResult.Success) {
                val response = result.data.data

                val newPosts = response.items
                    .map { post ->
                        when (post.modeLocation) {
                            PostModeLocation.PUBLIC -> {
                                PostReelItemModel(
                                    id = post.id,
                                    user = post.user,
                                    caption = post.caption,
                                    latitude = post.latitude,
                                    longitude = post.longitude,
                                    imageUrl = post.urlImage,
                                    locationName = post.locationName,
                                    modeLocation = post.modeLocation,
                                    type = post.type,
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
                                    imageUrl = post.urlImage,
                                    locationName = null,
                                    nsfw = post.nsfw,
                                    modeLocation = post.modeLocation,
                                    type = post.type,
                                    conversationId = post.conversation?.id,
                                    createdAt = post.createAt?.toLocalDateTime()
                                )
                            }
                        }
                    }

                postFeedStore.appendPage(newPosts, response.cursor)
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

    fun updateNsfwStatuses(statuses: Map<UUID, NsfwStatus>) {
        postFeedStore.updateNsfwStatuses(statuses)
    }

    fun addPost(post: PostReelItemModel) {
        postFeedStore.addPost(post)
    }

    fun deletePost(postId: UUID) {
        if (_deletePostState.value is UiState.Loading) return

        viewModelScope.launch {
            _deletePostState.value = UiState.Loading

            when (val result = postRepository.deletePost(postId)) {
                is DataResult.Success -> {
                    postFeedStore.removePost(postId)
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
