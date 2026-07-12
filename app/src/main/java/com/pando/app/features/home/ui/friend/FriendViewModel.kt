package com.pando.app.features.home.ui.friend

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.SearchItemModel
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.SearchResponse
import com.pando.app.features.home.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

sealed interface FriendResult {
    data class FriendListSuccess(val response: ApiResponse<FriendListResponse>) : FriendResult
    data class SearchState(val response: ApiResponse<SearchResponse>) : FriendResult
}

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val userRepository: UserRepository
) : BaseVM<FriendResult>() {

    private val searchQuery = MutableStateFlow("")

    init {
        observeSearchQuery()
    }

    fun getFriendList() {
        getData {
            when (val result = userRepository.getFriendList()) {
                is DataResult.Success -> {
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
            when (val result = userRepository.searchUsers(keyword)) {
                is DataResult.Success -> {
                    val total = result.data.data.total
                    DataSearchItem.total = total
                    val data = result.data.data.items

                    DataSearchItem.data.clear()

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
}