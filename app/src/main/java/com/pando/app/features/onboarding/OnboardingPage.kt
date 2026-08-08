package com.pando.app.features.onboarding

import androidx.annotation.DrawableRes
import com.pando.app.R

data class OnboardingPage(
    @param:DrawableRes val imageRes: Int,
    val title: String,
    val description: String
)
