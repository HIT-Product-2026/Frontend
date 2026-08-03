package com.pando.app.features.home.ui.privacy

import android.Manifest
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.location.LocationTrackingController
import com.pando.app.core.location.TrackingPreferences
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

    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    private val viewModel: PrivacyViewModel by viewModels()

    private var confirmedMode = UserMode.PUBLIC
    private var isApplyingApiResult = false
    private var requestedMode: UserMode? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                LocationTrackingController.hasLocationPermission(requireContext())

        if (granted) {
            submitModeUpdate(UserMode.PUBLIC)
        } else {
            requestedMode = null
            setSwitchChecked(false)
            setControlsEnabled(true)
            Toast.makeText(
                requireContext(),
                "Cần quyền vị trí để bắt đầu chia sẻ",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun initData() = Unit

    override fun initView() {
        confirmedMode = userSession.getCurrentUser()?.mode ?: UserMode.PUBLIC
        binding.locationSharingSwitch.isChecked =
            confirmedMode == UserMode.PUBLIC && trackingPreferences.isTrackingEnabled()
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

            if (isChecked) {
                requestLocationPermissionOrEnableSharing()
            } else {
                // Dừng thu thập trên thiết bị ngay khi người dùng tắt chia sẻ,
                // không chờ API đổi mode hoàn thành.
                trackingPreferences.setTrackingEnabled(false)
                LocationTrackingController.stop(requireContext())
                submitModeUpdate(UserMode.PRIVATE)
            }
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

                            if (state.data == UserMode.PUBLIC) {
                                trackingPreferences.setTrackingEnabled(true)
                                val started = LocationTrackingController.start(requireContext())

                                if (!started) {
                                    trackingPreferences.setTrackingEnabled(false)
                                    Toast.makeText(
                                        requireContext(),
                                        "Không thể bắt đầu chia sẻ. Hãy kiểm tra quyền vị trí.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                trackingPreferences.setTrackingEnabled(false)
                                LocationTrackingController.stop(requireContext())
                            }

                            setSwitchChecked(
                                state.data == UserMode.PUBLIC &&
                                    trackingPreferences.isTrackingEnabled()
                            )
                            requestedMode = null
                            setControlsEnabled(true)
                            viewModel.clearUpdateModeState()
                        }
                        is UiState.Error -> {
                            if (requestedMode == UserMode.PUBLIC) {
                                trackingPreferences.setTrackingEnabled(false)
                                LocationTrackingController.stop(requireContext())
                            }

                            // Nếu yêu cầu PRIVATE lỗi, vẫn giữ tracking local ở trạng thái
                            // dừng để tôn trọng thao tác của người dùng.
                            setSwitchChecked(
                                confirmedMode == UserMode.PUBLIC &&
                                    trackingPreferences.isTrackingEnabled()
                            )
                            requestedMode = null
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

    private fun requestLocationPermissionOrEnableSharing() {
        requestedMode = UserMode.PUBLIC

        if (LocationTrackingController.hasLocationPermission(requireContext())) {
            submitModeUpdate(UserMode.PUBLIC)
            return
        }

        setControlsEnabled(false)
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun submitModeUpdate(mode: UserMode) {
        requestedMode = mode
        setControlsEnabled(false)
        viewModel.updateUserMode(mode)
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
