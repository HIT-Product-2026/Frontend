package com.pando.app.features.home.ui.setting

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.FragmentSettingBinding
import com.pando.app.features.home.data.model.entity.DataChatMenuItem
import com.pando.app.features.home.data.model.entity.DataChatMessageItem
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.DataReceivedRequestItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.DataSentRequestItem
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {
    @Inject
    lateinit var tokenManager: TokenManager
    @Inject
    lateinit var userSession: UserSession
    @Inject
    lateinit var socketConnectionManager: SocketConnectionManager

    override fun initData() {
    }

    override fun initView() {
        loadCurrentUser()
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.logoutBtn.setOnClickListener {
            tokenManager.clear()
            userSession.clearCurrentUser()
            socketConnectionManager.disconnect()

            DataPostReelItem.apply {
                data.clear()
                total = 0
            }

            DataChatMenuItem.apply {
                data.clear()
                total = 0
            }

            DataChatMessageItem.apply {
                data.clear()
                total = 0
            }

            DataSentRequestItem.apply {
                data.clear()
                total = 0
            }

            DataFriendItem.apply {
                data.clear()
                total = 0
            }

            DataReceivedRequestItem.apply {
                data.clear()
                total = 0
            }

            DataSearchItem.apply {
                data.clear()
                total = 0
            }

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()

            findNavController().navigate(
                R.id.startFragment,
                null,
                navOptions
            )
        }

        binding.editBtn.setOnClickListener {
            findNavController().navigate(R.id.action_settingFragment_to_profileFragment)
        }
    }

    private fun loadCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.imgAvatar.loadAvatar(user?.avatar)
                    binding.displayNameTV.text = user?.displayName.orEmpty()
                    binding.usernameTV.text = user?.username.orEmpty()
                }
            }
        }
    }
}