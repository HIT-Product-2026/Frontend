package com.pando.app.core.extensions

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.pando.app.R

private const val PRESSED_SCALE = 0.90f
private const val PRESS_ANIMATION_DURATION = 100L

/**
 * Adds a small material-like lift and press animation without replacing the
 * view's existing click listener or background.
 */
fun View.applyButtonPressFeedback() {
    if (!isButtonLike() || getTag(R.id.tag_press_feedback_applied) == true) {
        return
    }

    setTag(R.id.tag_press_feedback_applied, true)
    elevation = elevation.coerceAtLeast(
        resources.getDimension(R.dimen.button_elevation)
    )
    stateListAnimator = createPressStateListAnimator()
}

/** Applies the feedback to button-like descendants after their listeners are attached. */
fun View.applyButtonPressFeedbackRecursively() {
    applyButtonPressFeedback()

    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).applyButtonPressFeedbackRecursively()
        }
    }
}

private fun View.isButtonLike(): Boolean {
    if (this is EditText) {
        return false
    }

    // TextView/ImageView also cover the custom text and image controls used by
    // the app (for example the auth and map controls).
    return this is Button ||
        hasButtonId() ||
        (isClickable && (this is TextView || this is ImageView))
}

private fun View.hasButtonId(): Boolean {
    if (id == View.NO_ID) {
        return false
    }

    val entryName = runCatching { resources.getResourceEntryName(id) }
        .getOrNull()
        ?.lowercase()
        ?: return false

    return entryName.contains("button") || entryName.contains("btn")
}

private fun View.createPressStateListAnimator(): StateListAnimator {
    val pressedAnimator = AnimatorSet().apply {
        duration = PRESS_ANIMATION_DURATION
        interpolator = AccelerateDecelerateInterpolator()
        playTogether(
            ObjectAnimator.ofFloat(this@createPressStateListAnimator, View.SCALE_X, PRESSED_SCALE),
            ObjectAnimator.ofFloat(this@createPressStateListAnimator, View.SCALE_Y, PRESSED_SCALE)
        )
    }

    val releasedAnimator = AnimatorSet().apply {
        duration = PRESS_ANIMATION_DURATION
        interpolator = AccelerateDecelerateInterpolator()
        playTogether(
            ObjectAnimator.ofFloat(this@createPressStateListAnimator, View.SCALE_X, 1f),
            ObjectAnimator.ofFloat(this@createPressStateListAnimator, View.SCALE_Y, 1f)
        )
    }

    return StateListAnimator().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressedAnimator)
        addState(intArrayOf(), releasedAnimator)
    }
}
