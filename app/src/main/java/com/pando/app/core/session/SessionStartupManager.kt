package com.pando.app.core.session

import android.util.Log
import com.auth0.android.jwt.DecodeException
import com.auth0.android.jwt.JWT
import com.pando.app.core.data.api.AuthApi
import com.pando.app.core.network.api.TokenManager
import com.pando.app.features.auth.data.model.request.RefreshTokenRequest
import com.pando.app.features.home.data.model.entity.CurrentUser
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
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
                decodeToken(accessToken)) {
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

    private fun clearSession() {
        tokenManager.clear()
        userSession.clearCurrentUser()
    }

    fun decodeToken(token: String?): Boolean {
        if (token.isNullOrEmpty()) {
            Log.e("JWT_DECODE", "Token null")
            return false
        }

        return try {
            val jwt = JWT(token)

            val isTokenExpired = jwt.isExpired(10)
            if (isTokenExpired) {
                Log.e("JWT_DECODE", "Token đã hết hạn sử dụng. Vui lòng đăng nhập lại")
                return false
            }

            val id = jwt.getClaim("id").asString()?.let(UUID::fromString) ?: return false
            val userName = jwt.getClaim("username").asString()
            val displayName = jwt.getClaim("displayName").asString()
            val userMode = jwt.getClaim("mode").asString()

            val mode: UserMode? = try {
                if (userMode != null) {
                    UserMode.valueOf(userMode.uppercase())
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                Log.e("JWT_DECODE", "mode từ Token không đúng định dạng: $userMode")
                null
            }

            val currentAvatar = userSession.getCurrentUser()
                ?.takeIf { it.id == id }
                ?.avatar

            userSession.setCurrentUser(
                CurrentUser(
                    id = id,
                    username = userName,
                    displayName = displayName,
                    mode = mode,
                    avatar = currentAvatar
                )
            )
            Log.d("JWT_DECODE", "Cập nhật User thành công")
            true
        } catch (e: DecodeException) {
            Log.e("JWT_DECODE", "Token không hợp lệ hoặc bị lỗi cấu trúc: ${e.message}")
            false
        }
    }
}