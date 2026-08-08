package com.pando.app.features.home.ui.center.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.response.FriendListResponse
import com.pando.app.features.home.data.model.response.LocationResponse
import com.pando.app.features.home.data.repository.FriendshipRepository
import com.pando.app.features.home.data.repository.LocationRepository
import com.pando.app.features.home.data.socket.MapSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val socketConnectionManager: SocketConnectionManager,
    private val mapSocket: MapSocket,
    private val friendshipRepository: FriendshipRepository,
    private val locationRepository: LocationRepository,
    private val userSession: UserSession
) : ViewModel() {

    companion object {
        private const val TAG = "MapService"
        private const val LOCATION_REFRESH_TTL_MILLIS = 30_000L
    }

    val connectionState = socketConnectionManager.connectionState

    private val _friends = MutableStateFlow<List<FriendItemModel>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _friendState =
        MutableStateFlow<UiState<ApiResponse<FriendListResponse>>>(UiState.Idle)
    val friendState: StateFlow<UiState<ApiResponse<FriendListResponse>>> =
        _friendState.asStateFlow()

    private var friendIds: Set<UUID> = emptySet()
    private var subscribedFriendIds: Set<UUID> = emptySet()
    private var hasLoadedFriendList = false
    private var friendListJob: Job? = null
    private var refreshLocationsJob: Job? = null
    private var lastLocationRefreshAt = 0L
    private var activeUserId: UUID? = null

    init {
        viewModelScope.launch {
            mapSocket.location.collect { location ->
                updateLocation(location)
            }
        }

        viewModelScope.launch {
            connectionState.collect { connectionState ->
                when (connectionState) {
                    SocketConnectionState.Connected -> {
                        syncLocationSubscriptions(friendIds)

                        // Bù những event có thể bị mất trong thời gian socket ngắt kết nối.
                        if (hasLoadedFriendList) {
                            requestFriendLocationRefresh(force = true)
                        }
                    }

                    // MapSocket keeps the desired friend IDs and recreates
                    // subscriptions on the next Connected state. Clearing
                    // them here created a race where a reconnect had no
                    // subscriptions left to restore.
                    SocketConnectionState.Disconnected,
                    is SocketConnectionState.Error -> Unit

                    SocketConnectionState.Connecting -> Unit
                }
            }
        }

        viewModelScope.launch {
            userSession.currentUser.collect { user ->
                val userId = user?.id
                if (userId == activeUserId) return@collect

                activeUserId = userId
                resetMapState()

                // Nếu ViewModel được tạo trước khi session được khôi phục,
                // tự tải danh sách ngay khi user xuất hiện.
                if (userId != null) {
                    getFriendList()
                }
            }
        }
    }

    private fun updateLocation(location: LocationResponse) {
        val oldItem = _friends.value.firstOrNull { item ->
            item.id == location.userId
        } ?: return

        if (!isNewerThan(location.lastActiveAt, oldItem.lastActiveAt)) return

        val updatedItem = oldItem.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            lastActiveAt = location.lastActiveAt
        )

        val updatedList = _friends.value.map { friend ->
            if (friend.id == location.userId) updatedItem else friend
        }

        publishFriends(updatedList)
    }

    fun getFriendList() {
        if (friendListJob?.isActive == true) return

        friendListJob = viewModelScope.launch {
            _friendState.value = UiState.Loading
            when (val result = friendshipRepository.getFriendList()) {
                is DataResult.Success -> {
                    val total = result.data.data.total
                    val data = result.data.data.items
                    val currentFriendsById = _friends.value.associateBy(FriendItemModel::id)
                    val friendList = if (total > 0) {
                        data.map { user ->
                            val currentFriend = currentFriendsById[user.id]
                            FriendItemModel(
                                id = user.id,
                                name = user.displayName.ifEmpty { user.username },
                                avatarUrl = user.avatarUrl?.takeIf(String::isNotBlank)
                                    ?: currentFriend?.avatarUrl,
                                longitude = currentFriend?.longitude,
                                latitude = currentFriend?.latitude,
                                lastActiveAt = currentFriend?.lastActiveAt
                            )
                        }
                    } else {
                        emptyList()
                    }

                    publishFriends(friendList)
                    friendIds = friendList.mapTo(linkedSetOf(), FriendItemModel::id)
                    hasLoadedFriendList = true

                    // Subscribe trước khi lấy snapshot để thu hẹp khoảng trống mất event.
                    if (connectionState.value == SocketConnectionState.Connected) {
                        syncLocationSubscriptions(friendIds)
                    }

                    requestFriendLocationRefresh(force = true)

                    _friendState.value = UiState.Success(result.data)
                }

                is DataResult.Error -> _friendState.value = UiState.Error(result.message)
            }
        }
    }

    fun refreshForMapResume() {
        if (hasLoadedFriendList) {
            requestFriendLocationRefresh(force = false)
        } else {
            getFriendList()
        }
    }

    private fun syncLocationSubscriptions(friendIds: Set<UUID>) {
        (subscribedFriendIds - friendIds).forEach(mapSocket::unsubscribeALocation)
        (friendIds - subscribedFriendIds).forEach(mapSocket::subscribeLocation)
        subscribedFriendIds = friendIds
    }

    private fun clearLocationSubscriptions() {
        mapSocket.unsubscribeAllLocation()
        subscribedFriendIds = emptySet()
    }

    private fun resetMapState() {
        friendListJob?.cancel()
        refreshLocationsJob?.cancel()
        friendListJob = null
        refreshLocationsJob = null
        clearLocationSubscriptions()
        friendIds = emptySet()
        hasLoadedFriendList = false
        lastLocationRefreshAt = 0L
        publishFriends(emptyList())
        _friendState.value = UiState.Idle
    }

    private fun requestFriendLocationRefresh(force: Boolean) {
        if (!hasLoadedFriendList) return
        if (friendIds.isEmpty()) return

        if (refreshLocationsJob?.isActive == true) {
            if (!force) return
            // Reconnect phải lấy snapshot mới thay vì chờ request cũ.
            refreshLocationsJob?.cancel()
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastLocationRefreshAt < LOCATION_REFRESH_TTL_MILLIS) {
            return
        }

        refreshLocationsJob = viewModelScope.launch {
            when (val result = locationRepository.getFriendLocations()) {
                is DataResult.Success -> {
                    mergeLocationSnapshot(result.data.data.items)
                    lastLocationRefreshAt = System.currentTimeMillis()
                }
                is DataResult.Error -> Log.e(
                    TAG,
                    "Không lấy được vị trí gần nhất: ${result.message}"
                )
            }
        }
    }

    private fun mergeLocationSnapshot(locations: List<LocationResponse>) {
        val locationsByUserId = locations.associateBy(LocationResponse::userId)
        val mergedFriends = _friends.value.map { friend ->
            val location = locationsByUserId[friend.id] ?: return@map friend

            if (!isNewerThan(location.lastActiveAt, friend.lastActiveAt)) {
                friend
            } else {
                friend.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    lastActiveAt = location.lastActiveAt
                )
            }
        }

        publishFriends(mergedFriends)
    }

    private fun isNewerThan(incoming: String, current: String?): Boolean {
        if (current == null) return true

        val incomingInstant = runCatching { Instant.parse(incoming) }.getOrNull()
        val currentInstant = runCatching { Instant.parse(current) }.getOrNull()

        return if (incomingInstant != null && currentInstant != null) {
            incomingInstant.isAfter(currentInstant)
        } else {
            // ISO-8601 có thể so sánh theo chuỗi khi backend không trả về format
            // mà Instant.parse hỗ trợ. Dữ liệu bằng nhau không cần render lại.
            incoming > current
        }
    }

    private fun publishFriends(friendList: List<FriendItemModel>) {
        _friends.value = friendList
    }

    override fun onCleared() {
        refreshLocationsJob?.cancel()
        clearLocationSubscriptions()
        super.onCleared()
    }
}
