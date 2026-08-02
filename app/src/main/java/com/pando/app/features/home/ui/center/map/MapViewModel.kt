package com.pando.app.features.home.ui.center.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.LocationUserResponseSocket
import com.pando.app.features.home.data.repository.FriendshipRepository
import com.pando.app.features.home.data.socket.MapSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val socketConnectionManager: SocketConnectionManager,
    private val mapSocket: MapSocket,
    private val friendshipRepository: FriendshipRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MapService"
    }
    val connectionState = socketConnectionManager.connectionState

    private val _friends = MutableStateFlow<List<FriendItemModel>>(emptyList())
    val friends = _friends.asStateFlow()

    fun subscribeLocationTopic(friendList: List<FriendItemModel>) {
        Log.d(TAG, "subscribeLocationTopic Đã gọi")
        friendList.forEach { item ->
            mapSocket.subscribeLocation(item.id)
        }
    }

    fun unsubscribeLocationTopic(friendId: UUID) {
        mapSocket.unsubscribeALocation(friendId)
    }

    fun unsubscribeAllLocationTopic(){
        mapSocket.unsubscribeAllLocation()
    }

    fun sendLocation(longitude: Double?, latitude: Double?) {
        if (longitude == null || latitude == null) return

        mapSocket.sendLocation(longitude, latitude)
    }

    init {
        viewModelScope.launch {
            mapSocket.location.collect { location ->
                updateLocation(location)
            }
        }
    }

    private fun updateLocation(location: LocationUserResponseSocket) {
        Log.i(TAG, "Update location update location")
        val oldItem = _friends.value.firstOrNull { item ->
            item.id == location.userId
        } ?: return

        val updatedItem = oldItem.copy(
            latitude = location.latitude,
            longitude = location.longitude
        )

        val updatedList = _friends.value
            .filterNot { it.id == location.userId } + updatedItem

        DataFriendItem.data.apply {
            clear()
            addAll(updatedList)
        }

        _friends.value = updatedList
    }

    private val _friendState = MutableStateFlow<UiState<ApiResponse<FriendListResponse>>>(UiState.Idle)
    val friendState: StateFlow<UiState<ApiResponse<FriendListResponse>>> = _friendState.asStateFlow()

    fun getFriendList() {
        viewModelScope.launch {
            _friendState.value = UiState.Loading
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

                        _friends.value = DataFriendItem.data.toList()
                    }

                    _friendState.value = UiState.Success(result.data)
                }
                is DataResult.Error -> _friendState.value = UiState.Error(result.message)
            }
        }
    }
}