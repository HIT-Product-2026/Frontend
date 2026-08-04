package com.pando.app.core.extensions

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.pando.app.R

fun Context.showComingSoon() {
    showShortToast(R.string.coming_soon)
}

fun Context.showShortToast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showShortToast(@StringRes messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
