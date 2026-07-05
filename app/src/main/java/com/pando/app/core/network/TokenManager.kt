package com.pando.app.core.network

import com.pando.app.core.data.local.AuthPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val authPreferences: AuthPreferences
) {
    fun getAccessToken(): String? = authPreferences.getAccessToken()

    fun saveAccessToken(token: String) {
        authPreferences.saveAuthSession(token)
    }

    fun clear() {
        authPreferences.clearSession()
    }
}