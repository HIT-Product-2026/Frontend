package com.pando.app.features.home.data.store

import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostFeedStore @Inject constructor() {
    private val _posts = MutableStateFlow<List<PostReelItemModel>>(emptyList())
    val posts = _posts.asStateFlow()

    private var hasLoadedFirstPage = false
    private var nextCursor: String? = null

    fun hasReachedEnd(): Boolean {
        return hasLoadedFirstPage && nextCursor == null
    }

    fun getNextCursor(): String? = nextCursor

    fun appendPage(posts: List<PostReelItemModel>, cursor: String?) {
        _posts.update { current ->
            val existingIds = current.mapTo(hashSetOf<UUID>()) { it.id }
            current + posts.filter { existingIds.add(it.id) }
        }
        hasLoadedFirstPage = true
        nextCursor = cursor
    }

    fun addPost(post: PostReelItemModel) {
        _posts.update { current ->
            if (current.any { it.id == post.id }) current else current + post
        }
    }

    fun removePost(postId: UUID) {
        _posts.update { current -> current.filterNot { it.id == postId } }
    }

    fun updateNsfwStatuses(statuses: Map<UUID, NsfwStatus>) {
        _posts.update { current ->
            current.map { post ->
                val status = statuses[post.id]
                if (status == null || status == post.nsfw) post else post.copy(nsfw = status)
            }
        }
    }

    fun reset() {
        _posts.value = emptyList()
        hasLoadedFirstPage = false
        nextCursor = null
    }
}
