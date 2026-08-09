package com.pando.app.features.auth.data.store

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the password needed by the register/send-otp API only for the current process.
 * Credentials are intentionally never written to a Bundle or persistent storage.
 */
@Singleton
class PendingRegistrationStore @Inject constructor() {
    private var email: String? = null
    private var password: String? = null

    @Synchronized
    fun save(email: String, password: String) {
        this.email = email.normalized()
        this.password = password
    }

    @Synchronized
    fun passwordFor(email: String): String? {
        return password.takeIf { this.email == email.normalized() }
    }

    @Synchronized
    fun clear(email: String) {
        if (this.email == email.normalized()) {
            this.email = null
            password = null
        }
    }

    private fun String.normalized(): String = trim().lowercase()
}
