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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ua.naiksoftware.stomp.StompClient
import java.util.UUID
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

    private val mapSubscriptions = mutableMapOf<UUID, ActiveSubscription>()

    private val _location = MutableSharedFlow<LocationResponse>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val location = _location.asSharedFlow()

    fun subscribeLocation(friendId: UUID) {

        val client = connectionManager.getConnectedClient() ?: run {
            Log.e(TAG, "Chưa kết nối")
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
                    Log.d(TAG, "Nhận message: ${topicMessage.payload}")

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
                }
            )

        mapSubscriptions[friendId] = ActiveSubscription(
            client = client,
            disposable = disposable
        )
    }

    fun unsubscribeALocation(friendId: UUID) {
        mapSubscriptions.remove(friendId)?.disposable?.dispose()
        Log.d(TAG, "Đã unsubscribe location của friend: $friendId")
    }

    fun unsubscribeAllLocation() {
        mapSubscriptions.values.forEach {
            it.disposable.dispose()
        }
        mapSubscriptions.clear()

        Log.d(TAG, "Đã unsubscribe tất cả location")
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
