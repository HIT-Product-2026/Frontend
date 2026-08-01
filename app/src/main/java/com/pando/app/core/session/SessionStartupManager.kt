package com.pando.app.core.session

import com.auth0.android.jwt.DecodeException
import com.auth0.android.jwt.JWT
import com.pando.app.core.data.api.AuthApi
import com.pando.app.core.network.api.TokenManager
import com.pando.app.features.auth.data.model.request.RefreshTokenRequest
import com.pando.app.features.home.data.model.entity.CurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class StartupSessionResult {
    AUTHENTICATED,
    NO_SESSION,
    SESSION_EXPIRED,
    NETWORK_ERROR
}

@Singleton
class SessionStartupManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val userSession: UserSession
) {
    suspend fun resolveSession(): StartupSessionResult =
        withContext(Dispatchers.IO) {
            val accessToken = tokenManager.getAccessToken()
            val refreshToken = tokenManager.getRefreshToken()

            if (accessToken.isNullOrBlank() && refreshToken.isNullOrBlank()) {
                return@withContext StartupSessionResult.NO_SESSION
            }

            if (!accessToken.isNullOrBlank() &&
                isAccessTokenUsable(accessToken)) {
                return@withContext StartupSessionResult.AUTHENTICATED
            }

            if (refreshToken.isNullOrBlank()) {
                clearSession()
                return@withContext StartupSessionResult.SESSION_EXPIRED
            }

            try {
                val response = authApi
                    .refreshToken(RefreshTokenRequest(refreshToken))
                    .execute()

                val responseBody = response.body()

                if (
                    !response.isSuccessful ||
                    responseBody?.success != true
                ) {
                    clearSession()
                    return@withContext StartupSessionResult.SESSION_EXPIRED
                }

                val tokens = responseBody.data

                if (tokens.accessToken.isBlank() ||
                    tokens.refreshToken.isBlank()) {
                    clearSession()
                    return@withContext StartupSessionResult.SESSION_EXPIRED
                }

                tokenManager.saveTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken
                )

                val refreshedUser = tokens.user

                userSession.setCurrentUser(
                    CurrentUser(
                        id = refreshedUser.id,
                        username = refreshedUser.username,
                        displayName = refreshedUser.displayName,
                        mode = refreshedUser.mode
                    )
                )

                StartupSessionResult.AUTHENTICATED
            } catch (_: IOException) {
                // Giữ token để người dùng có thể thử lại
                StartupSessionResult.NETWORK_ERROR
            }
        }

    private fun isAccessTokenUsable(accessToken: String): Boolean {
        return try {
            !JWT(accessToken).isExpired(30)
        } catch (_: DecodeException) {
            false
        }
    }

    private fun clearSession() {
        tokenManager.clear()
        userSession.clearCurrentUser()
    }
}