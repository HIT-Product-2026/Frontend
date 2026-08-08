package com.pando.app.features.home.data.socket

import android.util.Log
import com.google.gson.Gson
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.network.socket.SocketConstants
import com.pando.app.features.home.data.model.request.SendLocationRequest
import com.pando.app.features.home.data.model.response.LocationResponse
import io.reactivex.Completable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.StompClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapSocket @Inject constructor(
    private val connectionManager: SocketConnectionManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "MapSocket"
    }

    private val mapSubscriptions = ConcurrentHashMap<UUID, ActiveSubscription>()
    private val desiredFriendIds = ConcurrentHashMap.newKeySet<UUID>()
    private val retryJobs = ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _location = MutableSharedFlow<LocationResponse>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val location = _location.asSharedFlow()

    init {
        scope.launch {
            connectionManager.connectionState.collectLatest { state ->
                if (state == com.pando.app.core.state.SocketConnectionState.Connected) {
                    desiredFriendIds.toList().forEach(::subscribeLocation)
                } else {
                    clearActiveSubscriptions()
                }
            }
        }
    }

    @Synchronized
    fun subscribeLocation(friendId: UUID) {
        desiredFriendIds.add(friendId)

        val client = connectionManager.getConnectedClient() ?: run {
            return
        }

        val existing = mapSubscriptions[friendId]

        if (existing?.client === client && !existing.disposable.isDisposed) {
            Log.d(TAG, "Map đã subscribe trên client hiện tại")
            return
        }

        existing?.disposable?.dispose()
        mapSubscriptions.remove(friendId)

        val destination = "${SocketConstants.Chat.TOPIC_LOCATION}/$friendId"
        Log.d(TAG, "Subscribe destination: $destination")

        val disposable = client
            .topic(destination)
            .subscribe(
                { topicMessage ->
                    val message = runCatching {
                        gson.fromJson(topicMessage.payload, LocationResponse::class.java)
                    }.getOrElse { throwable ->
                        Log.e(TAG, "Không parse được Friend", throwable)
                        null
                    }

                    message?.let {
                        _location.tryEmit(it)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Lỗi subscribe Friend $friendId", throwable)
                    handleSubscriptionFailure(friendId, client)
                }
            )

        mapSubscriptions[friendId] = ActiveSubscription(
            client = client,
            disposable = disposable
        )
    }

    @Synchronized
    fun unsubscribeALocation(friendId: UUID) {
        desiredFriendIds.remove(friendId)
        retryJobs.remove(friendId)?.cancel()
        mapSubscriptions.remove(friendId)?.disposable?.dispose()
        Log.d(TAG, "Đã unsubscribe location của friend: $friendId")
    }

    @Synchronized
    fun unsubscribeAllLocation() {
        desiredFriendIds.clear()
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        mapSubscriptions.values.forEach {
            it.disposable.dispose()
        }
        mapSubscriptions.clear()

        Log.d(TAG, "Đã unsubscribe tất cả location")
    }

    @Synchronized
    private fun clearActiveSubscriptions() {
        mapSubscriptions.values.forEach { it.disposable.dispose() }
        mapSubscriptions.clear()
    }

    private fun handleSubscriptionFailure(friendId: UUID, client: StompClient) {
        synchronized(this) {
            val active = mapSubscriptions[friendId]
            if (active?.client === client) {
                mapSubscriptions.remove(friendId)?.disposable?.dispose()
            }
        }

        if (!desiredFriendIds.contains(friendId)) return
        if (retryJobs[friendId]?.isActive == true) return

        retryJobs[friendId] = scope.launch {
            delay(1_000L)
            retryJobs.remove(friendId)
            if (desiredFriendIds.contains(friendId) &&
                connectionManager.getConnectedClient() != null
            ) {
                subscribeLocation(friendId)
            }
        }
    }

    fun createSendLocationOperation(
        longitude: Double?,
        latitude: Double?
    ): Completable? {
        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
            return null
        }

        if (longitude == null || latitude == null) {
            Log.e(TAG, "Không có tọa độ để gửi")
            return null
        }

        val request = SendLocationRequest(
            longitude = longitude,
            latitude = latitude
        )

        val payload = gson.toJson(request)

        Log.d(TAG, "Bắt đầu gửi location")

        return client.send(
            SocketConstants.Chat.SEND_LOCATION_DESTINATION,
            payload
        ).doOnComplete {
                Log.d(TAG, "Đã gửi message lên STOMP")
            }
            .doOnError { throwable ->
                Log.e(TAG, "Không thể gửi message", throwable)
            }
    }

    private data class ActiveSubscription(
        val client: StompClient,
        val disposable: Disposable
    )
}
