package com.pando.app.features.onboarding

import android.content.Context
import androidx.core.content.edit

object OnboardingPreferences {
    private const val PREF_NAME = "pando_onboarding"
    private const val KEY_COMPLETED = "onboarding_completed"

    fun isCompleted(context: Context): Boolean {
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETED, false)
    }

    fun setCompleted(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_COMPLETED, true)
            }
    }
}