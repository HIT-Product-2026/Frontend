package com.pando.app.features.widget

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WidgetNavigationViewModel @Inject constructor() : ViewModel() {
    private val _replyTarget = MutableStateFlow(false)
    val replyTarget = _replyTarget.asStateFlow()

    fun goToTarget() {
        _replyTarget.value = true
    }

    fun handledTarget() {
        _replyTarget.value = false
    }
}