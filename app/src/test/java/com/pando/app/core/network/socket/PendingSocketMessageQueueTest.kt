package com.pando.app.core.network.socket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingSocketMessageQueueTest {
    @Test
    fun messagesAreReturnedInFifoOrder() {
        val queue = PendingSocketMessageQueue()
        val image = PendingSocketMessage("image", "first")
        val text = PendingSocketMessage("text", "second")

        queue.addLast(image)
        queue.addLast(text)

        assertEquals(image, queue.pollFirst())
        assertEquals(text, queue.pollFirst())
        assertNull(queue.pollFirst())
    }

    @Test
    fun failedMessageCanBePutBackAtTheFront() {
        val queue = PendingSocketMessageQueue()
        val image = PendingSocketMessage("image", "first")
        val text = PendingSocketMessage("text", "second")

        queue.addLast(image)
        queue.addLast(text)
        assertEquals(image, queue.pollFirst())
        queue.addFirst(image)

        assertEquals(image, queue.pollFirst())
        assertEquals(text, queue.pollFirst())
    }

    @Test
    fun clearDropsEveryPendingMessage() {
        val queue = PendingSocketMessageQueue()
        queue.addLast(PendingSocketMessage("image", "first"))
        queue.addLast(PendingSocketMessage("text", "second"))

        assertEquals(2, queue.clear())
        assertEquals(0, queue.size())
        assertNull(queue.pollFirst())
    }
}
