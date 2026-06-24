package com.pando.app.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.ui.UiState
import com.pando.app.core.utils.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseVM<T> : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<T>>(UiState.Idle)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    protected fun executeApi(apiCall: suspend () -> DataResult<T>) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            when (val result = apiCall()) {
                is DataResult.Success -> _uiState.value = UiState.Success(result.data)
                is DataResult.Error -> _uiState.value = UiState.Error(result.message)
            }
        }
    }

    fun updateState(state: UiState<T>) {
        _uiState.value = state
    }

    fun clearResult() {
        _uiState.value = UiState.Idle
    }
}