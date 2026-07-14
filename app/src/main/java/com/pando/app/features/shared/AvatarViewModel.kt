<<<<<<<< HEAD:app/src/main/java/com/pando/app/features/shared/AvatarViewModel.kt
package com.pando.app.features.shared
========
package com.pando.app.features.home.ui
>>>>>>>> 190d678 (feat: refactor AvatarViewModel and use for managing many user avatars, implement profile editing UI, and enhance settings layout):app/src/main/java/com/pando/app/features/home/ui/AvatarViewModel.kt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val avatarRepository: AvatarRepository
): ViewModel() {

    private val _avatars = MutableStateFlow<Map<UUID, ByteArray>>(emptyMap())

    val avatars = _avatars.asStateFlow()

    private val loadingIds = mutableSetOf<UUID>()

    fun loadAvatar(userId: UUID) {
        if (_avatars.value.containsKey(userId)) return
        if (!loadingIds.add(userId)) return

        viewModelScope.launch {
            when (val result = avatarRepository.getUserAvatar(userId)) {
                is DataResult.Success -> {
                    _avatars.update { current ->
                        current + (userId to result.data)
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