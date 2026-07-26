package com.pando.app.features.home.ui.map

import androidx.lifecycle.ViewModel
import com.pando.app.core.network.socket.SocketConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val socketConnectionManager: SocketConnectionManager
) : ViewModel() {
    val connectionState = socketConnectionManager.connectionState

    fun socketConnect() {
        socketConnectionManager.connect()
    }

    fun socketDisconnect() {
        socketConnectionManager.disconnect()
    }
}