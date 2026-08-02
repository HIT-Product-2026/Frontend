package com.pando.app.features.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.state.UiState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.Gender
import com.pando.app.features.home.data.repository.MediaRepository
import com.pando.app.features.home.data.repository.ProfileRepository
import com.pando.app.features.home.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository
) : BaseVM<ApiResponse<Void>>() {

    private val _avatarResult = MutableStateFlow<UiState<ApiResponse<Void>>>(UiState.Idle)
    val avatarResult: StateFlow<UiState<ApiResponse<Void>>> = _avatarResult.asStateFlow()

    fun updateProfile(
        displayName: String,
        birthday: String,
        gender: Gender,
        phoneNumber: String
    ) {
        getData {
            when (val userResult = userRepository.updateDisplayName(displayName)) {
                is DataResult.Error -> DataResult.Error(userResult.message)
                is DataResult.Success -> {
                    when (val profileResult = profileRepository.updateProfile(
                        birthday,
                        gender,
                        phoneNumber
                    )) {
                        is DataResult.Success -> DataResult.Success(profileResult.data)
                        is DataResult.Error -> DataResult.Error(profileResult.message)
                    }
                }
            }
        }
    }

    fun uploadAvatar(file: File) {
        val requestFile = file.asRequestBody(
            "image/*".toMediaTypeOrNull()
        )

        val body = MultipartBody.Part.createFormData(
            "file",
            file.name,
            requestFile
        )

        viewModelScope.launch {
            _avatarResult.value = UiState.Loading
            when (val result = mediaRepository.sendAvatar(body)) {
                is DataResult.Success -> _avatarResult.value = UiState.Success(result.data)
                is DataResult.Error -> _avatarResult.value = UiState.Error(result.message)
            }
        }
    }

    fun clearAvatarResult() {
        _avatarResult.value = UiState.Idle
    }
}
