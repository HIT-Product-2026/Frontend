package com.pando.app.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
@Suppress("DEPRECATION")
class AuthPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    companion object {
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
    }

    fun saveAuthSession(token: String) {
        sharedPreferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession() {
        sharedPreferences.edit { clear() }
    }
}