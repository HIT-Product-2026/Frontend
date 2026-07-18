package com.pando.app.core.session

import com.pando.app.features.home.data.model.entity.CurrentUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSession @Inject constructor() {
    private val _currentUser = MutableStateFlow<CurrentUser?>(null)

    val currentUser = _currentUser.asStateFlow()

    fun setCurrentUser(user: CurrentUser) {
        _currentUser.value = user
    }

    fun updateCurrentUser(transform: (CurrentUser) -> CurrentUser) {
        val user = _currentUser.value ?: return
        _currentUser.value = transform(user)
    }

    fun clearCurrentUser() {
        _currentUser.value = null
    }

    fun getCurrentUser(): CurrentUser? {
        return _currentUser.value
    }

    fun updateAvatar(avatar: ByteArray?) {
        updateCurrentUser { user ->
            user.copy(avatar = avatar)
        }
    }
    fun getCurrentUserId() = _currentUser.value?.id

    val isLoggedIn: Boolean
        get() = _currentUser.value != null
}