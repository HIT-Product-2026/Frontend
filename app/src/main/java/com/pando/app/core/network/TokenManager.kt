package com.pando.app.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("pando_prefs", Context.MODE_PRIVATE)

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun saveAccessToken(token: String) {
        prefs.edit { putString("access_token", token) }
    }

    fun clear() = prefs.edit { clear() }
}