package com.pando.app.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.ui.UiState
import com.pando.app.core.utils.DataResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseVM<T> : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<T>>(UiState.Idle)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<ViewModelEvent>()
    val event: SharedFlow<ViewModelEvent> = _event.asSharedFlow()

    protected fun getData(dataCall: suspend () -> DataResult<T>) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            when (val result = dataCall()) {
                is DataResult.Success -> _uiState.value = UiState.Success(result.data)
                is DataResult.Error -> _uiState.value = UiState.Error(result.message)
            }
        }
    }

    protected fun sendEvent(event: ViewModelEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    fun updateState(state: UiState<T>) {
        _uiState.value = state
    }

    fun clearResult() {
        _uiState.value = UiState.Idle
    }

    sealed interface ViewModelEvent {
        data class ShowSnackbar(val message : String) : ViewModelEvent
        data class Navigate(val actionId : Int) : ViewModelEvent
    }
}