package com.pando.app.features.auth.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRegistrationStoreTest {
    private val store = PendingRegistrationStore()

    @Test
    fun `returns password only for matching email`() {
        store.save(" User@Example.com ", "secret")

        assertEquals("secret", store.passwordFor("user@example.com"))
        assertNull(store.passwordFor("other@example.com"))
    }

    @Test
    fun `new registration replaces previous credentials`() {
        store.save("first@example.com", "first-password")
        store.save("second@example.com", "second-password")

        assertNull(store.passwordFor("first@example.com"))
        assertEquals("second-password", store.passwordFor("second@example.com"))
    }

    @Test
    fun `clear removes only matching registration`() {
        store.save("user@example.com", "secret")

        store.clear("other@example.com")
        assertEquals("secret", store.passwordFor("user@example.com"))

        store.clear("USER@example.com")
        assertNull(store.passwordFor("user@example.com"))
    }
}
