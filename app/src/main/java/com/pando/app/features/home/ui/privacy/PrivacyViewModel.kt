package com.pando.app.features.home.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.home.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _updateModeState = MutableStateFlow<UiState<UserMode>>(UiState.Idle)
    val updateModeState: StateFlow<UiState<UserMode>> = _updateModeState.asStateFlow()

    fun updateUserMode(mode: UserMode) {
        viewModelScope.launch {
            _updateModeState.value = UiState.Loading

            when (val result = userRepository.updateUserMode(mode)) {
                is DataResult.Success -> _updateModeState.value = UiState.Success(mode)
                is DataResult.Error -> _updateModeState.value = UiState.Error(result.message)
            }
        }
    }

    fun clearUpdateModeState() {
        _updateModeState.value = UiState.Idle
    }
}