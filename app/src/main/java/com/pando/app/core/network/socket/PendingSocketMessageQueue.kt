package com.pando.app.core.network.socket

import java.util.ArrayDeque

/** A small in-memory FIFO used while the STOMP connection is unavailable. */
internal data class PendingSocketMessage(
    val destination: String,
    val payload: String
)

internal class PendingSocketMessageQueue {
    private val messages = ArrayDeque<PendingSocketMessage>()

    @Synchronized
    fun addLast(message: PendingSocketMessage) {
        messages.addLast(message)
    }

    @Synchronized
    fun addFirst(message: PendingSocketMessage) {
        messages.addFirst(message)
    }

    @Synchronized
    fun pollFirst(): PendingSocketMessage? {
        return if (messages.isEmpty()) null else messages.removeFirst()
    }

    @Synchronized
    fun isEmpty(): Boolean = messages.isEmpty()
}
