package com.pando.app.features.home.ui.center.postreel

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.response.ChatMessageResponse
import com.pando.app.features.home.data.model.response.PostsResponse
import com.pando.app.features.home.data.repository.ConversationRepository
import com.pando.app.features.home.data.repository.PostRepository
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
    private val conversationRepository: ConversationRepository
) : BaseVM<PostEvent>() {
    private var isLoading = false
    private val _images = MutableStateFlow<Map<UUID, ByteArray>>(emptyMap())

    val images = _images.asStateFlow()

    private val loadingIds = mutableSetOf<UUID>()

    fun loadPost(userId: UUID) {
        if (_images.value.containsKey(userId)) {
            return
        }
        if (!loadingIds.add(userId)) return

        viewModelScope.launch {
            when (val result = postRepository.getPostImage(userId)) {
                is DataResult.Success -> {
                    _images.update { current ->
                        current + (userId to result.data)
                    }
                }

                is DataResult.Error -> {
                    // Emit event
                }
            }

            loadingIds.remove(userId)
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
                            modeLocation = post.modeLocation
                        )
                    }

                DataPostReelItem.data.addAll(newPosts)
                DataPostReelItem.hasLoadedFirstPage = true
                DataPostReelItem.nextCursor = result.data.data.cursor
            }

            isLoading = false

            when (result) {
                is DataResult.Success -> DataResult.Success(PostEvent.GetPostEvent(result.data))
                is DataResult.Error -> result
            }
        }
    }

    fun sendImagePost(conversationId: UUID, image: ByteArray) {
        getData {
            when (val result = conversationRepository.sendImageMessage(conversationId, image)) {
                is DataResult.Success -> DataResult.Success(PostEvent.SendImagePost(result.data))
                is DataResult.Error -> result
            }
        }
    }
}

sealed interface PostEvent {
    data class GetPostEvent(val response : ApiResponse<PostsResponse>) : PostEvent
    data class SendImagePost(val response: ApiResponse<ChatMessageResponse>) : PostEvent
}