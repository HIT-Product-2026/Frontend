package com.pando.app.core.network.api

import com.pando.app.core.data.local.AuthPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val authPreferences: AuthPreferences
) {
    fun getAccessToken(): String? = authPreferences.getAccessToken()

    fun getRefreshToken(): String? = authPreferences.getRefreshToken()

    fun saveTokens(accessToken: String, refreshToken: String) {
        authPreferences.saveAuthSession(accessToken, refreshToken)
    }

    fun clear() {
        authPreferences.clearSession()
    }
}