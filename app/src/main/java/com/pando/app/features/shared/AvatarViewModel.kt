package com.pando.app.features.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pando.app.core.session.UserSession
import com.pando.app.core.utils.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AvatarViewModel @Inject constructor (
    private val avatarRepository: AvatarRepository,
    private val userSession: UserSession
): ViewModel() {

    private val _avatars = MutableStateFlow<Map<UUID, String>>(emptyMap())

    val avatars = _avatars.asStateFlow()

    private val loadingIds = mutableSetOf<UUID>()

    fun loadAvatar(userId: UUID) {
        if (_avatars.value.containsKey(userId)) {
            val avatar = _avatars.value[userId]

            if (userSession.getCurrentUserId() == userId) {
                userSession.updateAvatar(avatar)
            }
            return
        }
        if (!loadingIds.add(userId)) return

        viewModelScope.launch {
            when (val result = avatarRepository.getUserAvatar(userId)) {
                is DataResult.Success -> {
                    _avatars.update { current ->
                        current + (userId to result.data.data)
                    }

                    if (userSession.getCurrentUserId() == userId) {
                        userSession.updateAvatar(result.data.data)
                    }
                }

                is DataResult.Error -> {
                    // Emit event
                }
            }

            loadingIds.remove(userId)
        }
    }

    fun loadAvatars(userIds: Collection<UUID>) {
        userIds.distinct().forEach(::loadAvatar)
    }
}