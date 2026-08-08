package com.pando.app.core.location

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LocationNavigationViewModel @Inject constructor() : ViewModel() {
    private val _focusCurrentLocation = MutableStateFlow(false)
    val focusCurrentLocation = _focusCurrentLocation.asStateFlow()

    fun requestCurrentLocationFocus() {
        _focusCurrentLocation.value = true
    }

    fun currentLocationFocused() {
        _focusCurrentLocation.value = false
    }
}
