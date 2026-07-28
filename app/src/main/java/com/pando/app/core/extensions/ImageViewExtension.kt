package com.pando.app.core.extensions

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.pando.app.R

fun ImageView.loadAvatar(avatar: Any?) {
    Glide.with(this)
        .load(avatar)
        .placeholder(R.drawable.ic_default_avatar)
        .error(R.drawable.ic_default_avatar)
        .circleCrop()
        .into(this)
}