package com.pando.app.core.service

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class FcmTokenSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val userSession: UserSession
) {
    companion object {
        private const val TAG = "FcmTokenSync"
        private const val PREFERENCES_NAME = "fcm_token_sync"
        private const val PENDING_TOKEN_KEY = "pending_token"
        private val RETRY_DELAYS_MILLIS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L)
    }

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Any()

    @Volatile
    private var syncJob: Job? = null

    @Volatile
    private var syncRequestedWhileRunning = false

    @Volatile
    private var lastSyncedKey: String? = null

    /**
     * Call only after login/session restoration has saved a valid access token
     * and populated UserSession.
     */
    fun syncAfterAuthentication() {
        requestSync()
    }

    /** Persist first because FirebaseMessagingService can be destroyed quickly. */
    fun onNewToken(token: String) {
        val normalizedToken = token.trim()
        if (normalizedToken.isEmpty()) return

        savePendingToken(normalizedToken)
        requestSync()
    }

    private fun requestSync() {
        synchronized(syncLock) {
            if (syncJob?.isActive == true) {
                syncRequestedWhileRunning = true
                return
            }

            syncRequestedWhileRunning = false
            syncJob = scope.launch {
                try {
                    syncWithRetry()
                } finally {
                    val shouldRunAgain = synchronized(syncLock) {
                        syncJob = null
                        syncRequestedWhileRunning.also {
                            syncRequestedWhileRunning = false
                        }
                    }
                    if (shouldRunAgain) {
                        requestSync()
                    }
                }
            }
        }
    }

    private suspend fun syncWithRetry() {
        repeat(RETRY_DELAYS_MILLIS.size + 1) { attempt ->
            if (!hasAuthenticatedSession()) return

            try {
                val userId = userSession.getCurrentUser()?.id ?: return
                val storedToken = pendingToken()
                val token = storedToken ?: fetchFirebaseToken() ?: return
                val syncKey = "$userId:$token"

                if (storedToken == null && lastSyncedKey == syncKey) return

                if (storedToken == null) {
                    savePendingToken(token)
                }

                when (val result = authRepository.sendFcmToken(token)) {
                    is DataResult.Success -> {
                        if (userSession.getCurrentUser()?.id == userId) {
                            clearPendingToken(token)
                            lastSyncedKey = syncKey
                            Log.d(TAG, "FCM token synchronized")
                            return
                        }
                    }

                    is DataResult.Error -> {
                        Log.w(TAG, "FCM token sync failed: ${result.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FCM token sync request failed", e)
            }

            if (attempt < RETRY_DELAYS_MILLIS.size) {
                delay(RETRY_DELAYS_MILLIS[attempt])
            }
        }

        Log.w(TAG, "FCM token remains pending after retries")
    }

    @Suppress("DEPRECATION")
    private suspend fun fetchFirebaseToken(): String? =
        FirebaseMessaging.getInstance().token.await()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun hasAuthenticatedSession(): Boolean =
        userSession.getCurrentUser() != null &&
            !tokenManager.getAccessToken().isNullOrBlank()

    private fun pendingToken(): String? =
        preferences.getString(PENDING_TOKEN_KEY, null)
            ?.takeIf(String::isNotBlank)

    private fun savePendingToken(token: String) {
        preferences.edit().putString(PENDING_TOKEN_KEY, token).apply()
        lastSyncedKey = null
    }

    private fun clearPendingToken(expectedToken: String) {
        if (pendingToken() == expectedToken) {
            preferences.edit().remove(PENDING_TOKEN_KEY).apply()
        }
    }
}
