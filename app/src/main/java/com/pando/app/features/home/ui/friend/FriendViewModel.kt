package com.pando.app.features.home.ui.friend

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.dto.FriendshipDto
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataReceivedRequestItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.DataSentRequestItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.ReceivedRequestItemModel
import com.pando.app.features.home.data.model.entity.SearchItemModel
import com.pando.app.features.home.data.model.entity.SentRequestItemModel
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.RequestedUsersResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import com.pando.app.features.home.data.repository.FriendshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val friendshipRepository: FriendshipRepository
) : BaseVM<FriendResult>() {
    private val searchQuery = MutableStateFlow("")

    private val _actionStates = MutableStateFlow<Map<UUID, FriendActionState>>(emptyMap())
    val actionStates = _actionStates.asStateFlow()

    private val _friendEvent = MutableSharedFlow<FriendEvent>()
    val friendEvent = _friendEvent.asSharedFlow()

    private fun updateActionState(
        targetId: UUID,
        action: FriendAction,
        isLoading: Boolean,
        errorMessage: String? = null
    ) {
        _actionStates.value = _actionStates.value.toMutableMap().apply {
            this[targetId] = FriendActionState(
                targetId,
                action,
                isLoading,
                errorMessage
            )
        }
    }

    private suspend fun sendFriendEvent(event: FriendEvent) {
        _friendEvent.emit(event)
    }

    fun clearActionState(targetId: UUID) {
        _actionStates.value -= targetId
    }

    //Unfriend
    fun unfriend(friendId: UUID) {
        getData {
            when (val result = friendshipRepository.unfriend(friendId)) {
                is DataResult.Success -> {
                    DataResult.Success(FriendResult.UnfriendSuccess(result.data))
                }
                is DataResult.Error -> {
                    DataResult.Error(result.message)
                }
            }
        }
    }

    // Search Users
    init {
        observeSearchQuery()
    }

    fun onSearchQueryChanged(keyword: String) {
        searchQuery.value = keyword.trim()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        searchQuery
            .debounce(500.milliseconds)
            .distinctUntilChanged()
            .onEach { keyword ->
                if (keyword.isBlank()) {
                    clearResult()
                } else {
                    searchUser(keyword)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun searchUser(keyword: String) {
        getData {
            when (val result = friendshipRepository.searchUsers(keyword)) {
                is DataResult.Success -> {
                    DataSearchItem.apply {
                        data.clear()
                        total = 0
                    }

                    val total = result.data.data.total
                    DataSearchItem.total = total

                    val data = result.data.data.items
                    if (total > 0) {
                        data.forEach { item ->
                            DataSearchItem.data.add(
                                SearchItemModel(
                                    item.id,
                                    item.displayName
                                )
                            )
                        }
                    }

                    DataResult.Success(FriendResult.SearchState(result.data))
                }

                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun searchAgain(keyword: String) {
        val value = keyword.trim()

        if (value.isNotBlank()) {
            searchUser(value)
        }
    }

    // FriendList
    fun getFriendList() {
        getData {
            when (val result = friendshipRepository.getFriendList()) {
                is DataResult.Success -> {
                    DataFriendItem.apply {
                        data.clear()
                        total = 0
                    }

                    val total = result.data.data.total
                    DataFriendItem.total = total

                    val data = result.data.data.items
                    if (total > 0) {
                        data.forEach { item ->
                            DataFriendItem.data.add(
                                FriendItemModel(
                                    item.id,
                                    item.displayName.ifEmpty { item.username }
                                )
                            )
                        }
                    }

                    DataResult.Success(FriendResult.FriendListSuccess(result.data))
                }

                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun getSentRequestedUsers() {
        getData {
            when (val result = friendshipRepository.getSentRequestedUsers()) {
                is DataResult.Success -> {
                    DataSentRequestItem.apply {
                        data.clear()
                        total = 0
                    }

                    val total = result.data.data.total
                    DataSentRequestItem.total = total

                    val data = result.data.data.items
                    if (total > 0) {
                        data.forEach { item ->
                            DataSentRequestItem.data.add(
                                SentRequestItemModel(
                                    item.receiver.id,
                                    item.receiver.displayName.ifEmpty { item.receiver.username },
                                    item.id
                                )
                            )
                        }
                    }
                    DataResult.Success(FriendResult.SentRequestedUsersSuccess(result.data))
                }

                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun getReceivedRequestedUsers() {
        getData {
            when (val result = friendshipRepository.getReceivedRequestedUsers()) {
                is DataResult.Success -> {
                    DataReceivedRequestItem.apply {
                        data.clear()
                        total = 0
                    }

                    val total = result.data.data.total
                    DataReceivedRequestItem.total = total

                    val data = result.data.data.items
                    if (total > 0) {
                        data.forEach { item ->
                            DataReceivedRequestItem.data.add(
                                ReceivedRequestItemModel(
                                    item.requester.id,
                                    item.requester.displayName.ifEmpty { item.requester.username },
                                    item.id
                                )
                            )
                        }
                    }
                    DataResult.Success(FriendResult.ReceivedRequestedUsersSuccess(result.data))
                }

                is DataResult.Error -> DataResult.Error(result.message)
            }
        }
    }

    fun requestFriend(userId: UUID) {
        val currentState = _actionStates.value[userId]

        if (currentState?.isLoading == true) return

        viewModelScope.launch {
            updateActionState(userId, FriendAction.REQUEST, true)

            when (val result = friendshipRepository.requestFriend(userId)) {
                is DataResult.Success -> {
                    updateActionState(userId, FriendAction.REQUEST, false)

                    sendFriendEvent(FriendEvent.RequestFriendSuccess(result.data))
                }

                is DataResult.Error -> {
                    updateActionState(userId, FriendAction.REQUEST, false, result.message)
                }
            }
        }
    }

    fun acceptFriend(friendshipId: UUID) {
        val currentState = _actionStates.value[friendshipId]

        if (currentState?.isLoading == true) return

        viewModelScope.launch {
            updateActionState(friendshipId, FriendAction.ACCEPT, true)

            when (val result = friendshipRepository.acceptFriend(friendshipId)) {
                is DataResult.Success -> {
                    updateActionState(friendshipId, FriendAction.ACCEPT, false)

                    sendFriendEvent(FriendEvent.AcceptFriendSuccess(result.data))
                }

                is DataResult.Error -> {
                    updateActionState(friendshipId, FriendAction.ACCEPT, false, result.message)
                }
            }
        }
    }

    fun rejectFriend(friendshipId: UUID) {
        val currentState = _actionStates.value[friendshipId]

        if (currentState?.isLoading == true) return

        viewModelScope.launch {
            updateActionState(friendshipId, FriendAction.REJECT, true)

            when (val result = friendshipRepository.rejectFriend(friendshipId)) {
                is DataResult.Success -> {
                    updateActionState(friendshipId, FriendAction.REJECT, false)

                    sendFriendEvent(FriendEvent.RejectFriendSuccess(result.data))
                }

                is DataResult.Error -> {
                    updateActionState(friendshipId, FriendAction.REJECT, false, result.message)
                }
            }
        }
    }
}

sealed interface FriendResult {
    data class FriendListSuccess(val response: ApiResponse<FriendListResponse>) : FriendResult
    data class SentRequestedUsersSuccess(val response: ApiResponse<RequestedUsersResponse>) :
        FriendResult

    data class ReceivedRequestedUsersSuccess(val response: ApiResponse<RequestedUsersResponse>) :
        FriendResult
    data class UnfriendSuccess(val response: ApiResponse<FriendshipDto>) : FriendResult
    data class SearchState(val response: ApiResponse<SearchResponse>) : FriendResult
}

sealed interface FriendEvent {
    data class RequestFriendSuccess(val response: ApiResponse<FriendshipDto>) : FriendEvent

    data class AcceptFriendSuccess(val response: ApiResponse<FriendshipDto>) : FriendEvent

    data class RejectFriendSuccess(val response: ApiResponse<FriendshipDto>) : FriendEvent
}

enum class FriendAction {
    REQUEST,
    ACCEPT,
    REJECT
}

data class FriendActionState(
    val targetId: UUID,
    val action: FriendAction,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)