package com.pando.app.features.auth.ui.register

import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.auth.data.repository.AuthRepository
import com.pando.app.features.auth.data.store.PendingRegistrationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingRegistrationStore: PendingRegistrationStore
) : BaseVM<ApiResponse<Void>>() {
    fun register(email: String, password: String, confirmPassword: String) {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            updateState(UiState.Error("Please fill in all fields"))
            return
        }

        if (password != confirmPassword) {
            updateState(UiState.Error("Passwords do not match"))
            return
        }

        getData {
            authRepository.registerSendOtp(email, password).also { result ->
                if (result is DataResult.Success) {
                    pendingRegistrationStore.save(email, password)
                }
            }
        }
    }
}
