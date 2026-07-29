package com.pando.app.features.home.ui.center.postreel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.response.PostsResponse
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
) : BaseVM<ApiResponse<PostsResponse>>() {
    val connectionState = socketConnectionManager.connectionState
    private var isLoading = false

    private val _images = MutableStateFlow<Map<UUID, String>>(emptyMap())
    val images = _images.asStateFlow()

    private val loadingIds = mutableSetOf<UUID>()

    fun loadPost(postId: UUID) {
        if (_images.value.containsKey(postId)) {
            return
        }
        if (!loadingIds.add(postId)) return

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

            loadingIds.remove(postId)
        }
    }

    fun loadPosts(postIds: Collection<UUID>) {
        postIds.distinct().forEach(::loadPost)
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
                        PostReelItemModel(
                            id = post.id,
                            user = post.user,
                            caption = post.caption,
                            latitude = post.latitude,
                            longitude = post.longitude,
                            modeLocation = post.modeLocation,
                            conversationId = post.conversation?.id
                        )
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
}