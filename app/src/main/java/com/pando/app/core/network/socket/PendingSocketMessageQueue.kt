package com.pando.app.core.network.socket

import java.util.ArrayDeque
import java.util.UUID

/** A small in-memory FIFO used while the STOMP connection is unavailable. */
internal data class PendingSocketMessage(
    val destination: String,
    val payload: String,
    val ownerUserId: UUID? = null
)

internal class PendingSocketMessageQueue {
    companion object {
        const val DEFAULT_MAX_SIZE = 256
    }

    private val messages = ArrayDeque<PendingSocketMessage>()

    @Synchronized
    fun addLast(message: PendingSocketMessage): Boolean {
        if (messages.size >= DEFAULT_MAX_SIZE) return false
        messages.addLast(message)
        return true
    }

    @Synchronized
    fun addFirst(message: PendingSocketMessage): Boolean {
        // A failed item was already accepted by the queue. It must never be
        // dropped just because new items filled the bounded tail meanwhile.
        messages.addFirst(message)
        return true
    }

    @Synchronized
    fun pollFirst(): PendingSocketMessage? {
        return if (messages.isEmpty()) null else messages.removeFirst()
    }

    @Synchronized
    fun isEmpty(): Boolean = messages.isEmpty()

    @Synchronized
    fun size(): Int = messages.size

    @Synchronized
    fun clear(): Int {
        val removedCount = messages.size
        messages.clear()
        return removedCount
    }
}
