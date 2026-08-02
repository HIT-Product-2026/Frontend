package com.pando.app.core.network.api

import com.pando.app.core.data.api.AuthApi
import com.pando.app.core.session.UserSession
import com.pando.app.features.auth.data.model.request.RefreshTokenRequest
import com.pando.app.features.home.data.model.entity.CurrentUser
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val userSession: UserSession
) : Authenticator {

    private val lock = Any()

    override fun authenticate(
        route: Route?,
        response: Response
    ): Request? {
        // Không retry quá một lần
        if (responseCount(response) >= 2) {
            return null
        }

        synchronized(lock) {
            val failedToken = response
                .request
                .header("Authorization")
                ?.removePrefix("Bearer ")

            val currentToken = tokenManager.getAccessToken()

            // Request khác đã refresh trong lúc request hiện tại chờ khóa
            if (!currentToken.isNullOrBlank() &&
                currentToken != failedToken) {
                return retryRequest(
                    response = response,
                    accessToken = currentToken
                )
            }

            val refreshToken = tokenManager.getRefreshToken() ?: return clearSessionAndStop()

            return try {
                val refreshResponse = authApi
                    .refreshToken(RefreshTokenRequest(refreshToken))
                    .execute()

                val responseBody = refreshResponse.body()

                if (!refreshResponse.isSuccessful || responseBody?.success != true) {
                    return clearSessionAndStop()
                }

                val tokens = responseBody.data

                if (tokens.accessToken.isBlank() || tokens.refreshToken.isBlank()) {
                    return clearSessionAndStop()
                }

                tokenManager.saveTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken
                )

                val refreshedUser = tokens.user

                val previousUser = userSession.getCurrentUser()
                    ?.takeIf { currentUser ->
                        currentUser.id == refreshedUser.id
                    }

                userSession.setCurrentUser(
                    CurrentUser(
                        id = refreshedUser.id,
                        username = refreshedUser.username,
                        displayName = refreshedUser.displayName,
                        mode = refreshedUser.mode,
                        avatar = previousUser?.avatar,
                        profile = previousUser?.profile
                    )
                )

                retryRequest(
                    response = response,
                    accessToken = tokens.accessToken
                )
            } catch (_: IOException) {
                // Lỗi mạng không đồng nghĩa refresh token đã hết hạn
                null
            }
        }
    }

    private fun retryRequest(response: Response, accessToken: String): Request {
        return response.request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun clearSessionAndStop(): Request? {
        tokenManager.clear()
        userSession.notifySessionExpired()
        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var previousResponse = response.priorResponse

        while (previousResponse != null) {
            count++
            previousResponse = previousResponse.priorResponse
        }

        return count
    }
}
