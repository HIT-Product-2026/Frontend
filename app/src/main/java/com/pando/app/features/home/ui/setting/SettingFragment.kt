package com.pando.app.features.home.ui.setting

import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.pando.app.MainViewModel
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.extensions.showComingSoon
import com.pando.app.core.extensions.showShortToast
import com.pando.app.core.location.LocationTrackingController
import com.pando.app.core.location.TrackingPreferences
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.FragmentSettingBinding
import com.pando.app.features.home.data.store.PostFeedStore
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
    lateinit var trackingPreferences: TrackingPreferences
    @Inject
    lateinit var postFeedStore: PostFeedStore
    private val mainViewModel: MainViewModel by activityViewModels()

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
            trackingPreferences.setTrackingEnabled(false)
            LocationTrackingController.stop(requireContext())
            tokenManager.clear()
            userSession.clearCurrentUser()
            mainViewModel.socketDisconnect()

            postFeedStore.reset()

            requireContext().showShortToast(R.string.logout_success)

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

        binding.privacyBtn.setOnClickListener {
            findNavController().navigate(R.id.action_settingFragment_to_privacyFragment)
        }

        listOf(
            binding.blockedAccountsBtn,
            binding.shareProfileBtn,
            binding.aboutBtn,
            binding.rateBtn,
            binding.reportIssueBtn,
            binding.privacyPolicyBtn,
            binding.deleteAccountBtn,
            binding.appearanceBtn
        ).forEach { view ->
            view.setOnClickListener {
                requireContext().showComingSoon()
            }
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
