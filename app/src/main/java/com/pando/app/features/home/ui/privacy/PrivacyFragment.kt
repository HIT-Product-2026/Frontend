package com.pando.app.features.home.ui.privacy

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentPrivacyBinding
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrivacyFragment : BaseFragment<FragmentPrivacyBinding>(FragmentPrivacyBinding::inflate) {

    @Inject
    lateinit var userSession: UserSession

    private val viewModel: PrivacyViewModel by viewModels()

    private var confirmedMode = UserMode.PUBLIC
    private var isApplyingApiResult = false

    override fun initData() = Unit

    override fun initView() {
        confirmedMode = userSession.getCurrentUser()?.mode ?: UserMode.PUBLIC
        binding.locationSharingSwitch.isChecked = confirmedMode == UserMode.PUBLIC
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.locationSharingRow.setOnClickListener {
            binding.locationSharingSwitch.toggle()
        }

        binding.locationSharingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingApiResult) return@setOnCheckedChangeListener

            setControlsEnabled(false)
            viewModel.updateUserMode(
                if (isChecked) UserMode.PUBLIC else UserMode.PRIVATE
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateModeState.collect { state ->
                    when (state) {
                        is UiState.Idle -> setControlsEnabled(true)
                        is UiState.Loading -> setControlsEnabled(false)
                        is UiState.Success -> {
                            confirmedMode = state.data
                            userSession.updateCurrentUser { user ->
                                user.copy(mode = state.data)
                            }
                            setSwitchChecked(state.data == UserMode.PUBLIC)
                            setControlsEnabled(true)
                            viewModel.clearUpdateModeState()
                        }
                        is UiState.Error -> {
                            setSwitchChecked(confirmedMode == UserMode.PUBLIC)
                            setControlsEnabled(true)
                            Toast.makeText(
                                requireContext(),
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.clearUpdateModeState()
                        }
                    }
                }
            }
        }
    }

    private fun setSwitchChecked(isChecked: Boolean) {
        isApplyingApiResult = true
        binding.locationSharingSwitch.isChecked = isChecked
        isApplyingApiResult = false
    }

    private fun setControlsEnabled(enabled: Boolean) {
        binding.locationSharingRow.isEnabled = enabled
        binding.locationSharingSwitch.isEnabled = enabled
    }
}