package com.pando.app.features.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentOnboardingBinding

class OnboardingFragment :
    BaseFragment<FragmentOnboardingBinding>(FragmentOnboardingBinding::inflate) {
    private val pages by lazy {
        listOf(
            OnboardingPage(
                R.mipmap.ic_launcher,
                "Chào mừng đến với PanDo",
                "Chia sẻ những khoảnh khắc và giữ kết nối với bạn bè."
            ),
            OnboardingPage(
                R.drawable.ic_default_avatar,
                "Tìm nhau trên bản đồ",
                "Cho phép PanDo sử dụng vị trí để hiển thị bạn trên bản đồ."
            ),
            OnboardingPage(
                R.drawable.ic_default_avatar,
                "Giữ kết nối khi chạy nền",
                "Cho phép vị trí mọi lúc để tiếp tục chia sẻ khi bạn rời khỏi màn hình PanDo."
            ),
            OnboardingPage(
                R.drawable.ic_default_avatar,
                "Không bỏ lỡ khoảnh khắc",
                "Nhận thông báo về tin nhắn, bài đăng và hoạt động của bạn bè."
            )
        )
    }

    private val foregroundLocationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            val coarseGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {
                moveToNextPage()
            } else {
                showLocationDeniedDialog()
            }
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Dù người dùng đồng ý hay từ chối vẫn cho tiếp tục.
            moveToNextPage()
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            finishOnboarding()
        }

    override fun initData() {
    }

    override fun initView() {
        binding.viewPagerOnboarding.adapter = OnboardingAdapter(pages)

        updateButtonText(0)
    }

    override fun initActionView() {
        initListener()
    }

    fun initListener() {
        binding.viewPagerOnboarding.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {
                    updateButtonText(position)
                }
            }
        )

        binding.btnContinue.setOnClickListener {
            handleContinueClick()
        }

        binding.tvSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun handleContinueClick() {
        when (binding.viewPagerOnboarding.currentItem) {
            PAGE_WELCOME -> moveToNextPage()

            PAGE_FOREGROUND_LOCATION -> {
                requestForegroundLocation()
            }

            PAGE_BACKGROUND_LOCATION -> {
                requestBackgroundLocation()
            }

            PAGE_NOTIFICATION -> {
                requestNotification()
            }
        }
    }

    private fun requestForegroundLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            moveToNextPage()
            return
        }

        foregroundLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            moveToNextPage()
            return
        }

        val foregroundGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        if (!foregroundGranted) {
            showNeedForegroundLocationDialog()
            return
        }

        val backgroundGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (backgroundGranted) {
            moveToNextPage()
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            showBackgroundLocationExplanation()
        }
    }

    private fun showBackgroundLocationExplanation() {
        val permissionLabel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireContext()
                    .packageManager
                    .backgroundPermissionOptionLabel
                    .toString()
            } else {
                "Cho phép mọi lúc"
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cho phép vị trí khi chạy nền")
            .setMessage(
                """
                Để tiếp tục chia sẻ vị trí khi PanDo không hiển thị trên màn hình, hãy chọn:

                Vị trí → $permissionLabel

                Bạn vẫn có thể sử dụng PanDo nếu không bật quyền này.
                """.trimIndent()
            )
            .setNegativeButton("Để sau") { _, _ ->
                moveToNextPage()
            }
            .setPositiveButton("Mở cài đặt") { _, _ ->
                openAppSettings()
            }
            .show()
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            finishOnboarding()
            return
        }

        val notificationGranted =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (notificationGranted) {
            finishOnboarding()
        } else {
            notificationLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts(
                "package",
                requireContext().packageName,
                null
            )
        )

        startActivity(intent)
    }

    private fun showLocationDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chưa có quyền vị trí")
            .setMessage(
                "PanDo vẫn có thể mở, nhưng bản đồ sẽ không hiển thị vị trí của bạn."
            )
            .setNegativeButton("Bỏ qua") { _, _ ->
                moveToNextPage()
            }
            .setPositiveButton("Thử lại") { _, _ ->
                requestForegroundLocation()
            }
            .show()
    }

    private fun showNeedForegroundLocationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cần quyền vị trí trước")
            .setMessage(
                "Bạn cần cho phép vị trí khi dùng ứng dụng trước khi bật vị trí nền."
            )
            .setNegativeButton("Bỏ qua") { _, _ ->
                moveToNextPage()
            }
            .setPositiveButton("Cho phép") { _, _ ->
                requestForegroundLocation()
            }
            .show()
    }

    private fun moveToNextPage() {
        val currentPage = binding.viewPagerOnboarding.currentItem

        if (currentPage < pages.lastIndex) {
            binding.viewPagerOnboarding.currentItem = currentPage + 1
        } else {
            finishOnboarding()
        }
    }

    private fun updateButtonText(position: Int) {
        binding.btnContinue.text = when (position) {
            PAGE_WELCOME -> "Tiếp tục"
            PAGE_FOREGROUND_LOCATION -> "Cho phép vị trí"
            PAGE_BACKGROUND_LOCATION -> "Cho phép khi chạy nền"
            PAGE_NOTIFICATION -> "Bật thông báo"
            else -> "Tiếp tục"
        }
    }

    private fun finishOnboarding() {
        OnboardingPreferences.setCompleted(requireContext())

        findNavController().navigate(R.id.action_onboardingFragment_to_startFragment)
    }

    companion object {
        private const val PAGE_WELCOME = 0
        private const val PAGE_FOREGROUND_LOCATION = 1
        private const val PAGE_BACKGROUND_LOCATION = 2
        private const val PAGE_NOTIFICATION = 3
    }

    override fun onResume() {
        super.onResume()

        if (binding.viewPagerOnboarding.currentItem == PAGE_BACKGROUND_LOCATION) {
            checkBackgroundPermissionAfterSettings()
        }
    }

    private fun checkBackgroundPermissionAfterSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            moveToNextPage()
        }
    }
}