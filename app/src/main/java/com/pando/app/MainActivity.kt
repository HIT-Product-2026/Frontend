package com.pando.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.core.network.sse.SseManager
import com.pando.app.core.location.LocationTrackingController
import com.pando.app.core.location.LocationNavigationViewModel
import com.pando.app.core.location.TrackingPreferences
import com.pando.app.core.session.SessionStartupManager
import com.pando.app.core.session.SessionState
import com.pando.app.core.session.StartupSessionResult
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.ActivityMainBinding
import com.pando.app.features.onboarding.OnboardingPreferences
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.widget.WidgetNavigationViewModel
import com.pando.app.features.widget.WidgetPendingIntentFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var userSession: UserSession

    @Inject
    lateinit var sessionStartupManager: SessionStartupManager

    @Inject
    lateinit var sseManager: SseManager

    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    private val widgetNavigationViewModel: WidgetNavigationViewModel by viewModels()
    private val locationNavigationViewModel: LocationNavigationViewModel by viewModels()

    private lateinit var navController: NavController

    //    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
//
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        val navHostFragment =
//            supportFragmentManager.findFragmentById(R.id.FragmentContainer) as NavHostFragment
//        val navController = navHostFragment.navController
//
//        sendFCMToken()
//        handleWidgetIntent(intent)
//
//        if (savedInstanceState == null) {
//            lifecycleScope.launch {
//                val result = sessionStartupManager.resolveSession()
//
//                val navGraph = navController.navInflater
//                    .inflate(R.navigation.nav_graph)
//
//                val startDestination = when (result) {
//                    StartupSessionResult.AUTHENTICATED -> {
//                        viewModel.socketConnect()
//                        sseManager.connect()
//                        R.id.centerFragment
//                    }
//
//                    StartupSessionResult.NO_SESSION,
//                    StartupSessionResult.SESSION_EXPIRED,
//                    StartupSessionResult.NETWORK_ERROR -> {
//                        viewModel.socketDisconnect()
//                        sseManager.disconnect()
//                        R.id.startFragment
//                    }
//                }
//
//                navGraph.setStartDestination(startDestination)
//                navController.graph = navGraph
//
//                when (result) {
//                    StartupSessionResult.SESSION_EXPIRED -> {
//                        Toast.makeText(
//                            this@MainActivity,
//                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//
//                    StartupSessionResult.NETWORK_ERROR -> {
//                        Toast.makeText(
//                            this@MainActivity,
//                            "Không thể kiểm tra phiên đăng nhập. Vui lòng kiểm tra kết nối mạng.",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//
//                    else -> Unit
//                }
//
//                observeSessionState(navController)
//            }
//        } else {
//            observeSessionState(navController)
//        }
//        observeUiEvents()
//    }
    override fun onCreate(savedInstanceState: Bundle?) {
        var startupCompleted = false

        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !startupCompleted
        }

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.FragmentContainer) as NavHostFragment

        navController = navHostFragment.navController

        sendFCMToken()
        observeUiEvents()

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val onboardingCompleted = OnboardingPreferences.isCompleted(this@MainActivity)

                if (!onboardingCompleted) {
                    setupStartDestination(
                        navController = navController,
                        startDestination = R.id.onboardingFragment
                    )

                    startupCompleted = true

                    observeSessionState(navController)
                    return@launch
                }

                val sessionResult = sessionStartupManager.resolveSession()

                val startDestination = when (sessionResult) {
                    StartupSessionResult.AUTHENTICATED -> {
                        viewModel.socketConnect()
                        sseManager.connect()

                        R.id.centerFragment
                    }

                    StartupSessionResult.NO_SESSION,
                    StartupSessionResult.SESSION_EXPIRED,
                    StartupSessionResult.NETWORK_ERROR -> {
                        viewModel.socketDisconnect()
                        sseManager.disconnect()

                        R.id.startFragment
                    }
                }

                setupStartDestination(
                    navController = navController,
                    startDestination = startDestination
                )

                showStartupMessage(sessionResult)
                observeSessionState(navController)

                startupCompleted = true
                handleNavigationIntent(intent)
            }
        } else {
            startupCompleted = true

            observeSessionState(navController)
            handleNavigationIntent(intent)
        }
    }

    private fun setupStartDestination(navController: NavController, startDestination: Int) {
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        navGraph.setStartDestination(startDestination)
        navController.graph = navGraph
    }

    private fun showStartupMessage(result: StartupSessionResult) {
        when (result) {
            StartupSessionResult.SESSION_EXPIRED -> {
                Toast.makeText(
                    this,
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                    Toast.LENGTH_LONG
                ).show()
            }

            StartupSessionResult.NETWORK_ERROR -> {
                Toast.makeText(
                    this,
                    "Không thể kiểm tra phiên đăng nhập. Vui lòng kiểm tra kết nối mạng.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> Unit
        }
    }

//    private fun observeSessionState(navController: NavController) {
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    userSession.sessionState.collect { state ->
//                        if (state == SessionState.EXPIRED) {
//                            viewModel.socketDisconnect()
//                            sseManager.disconnect()
//                            userSession.markSessionActive()
//
//                            val navOptions = NavOptions.Builder()
//                                .setPopUpTo(R.id.nav_graph, true)
//                                .build()
//
//                            navController.navigate(R.id.startFragment, null, navOptions)
//
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
//                                Toast.LENGTH_LONG
//                            ).show()
//                        }
//                    }
//                }
//                launch {
//                    userSession.currentUser.collect { user ->
//                        if (user != null) {
//                            viewModel.socketConnect()
//                            sseManager.connect()
//                            viewModel.loadCurrentUserProfile(user.id)
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun observeSessionState(navController: NavController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    userSession.sessionState.collect { state ->
                        val onboardingCompleted = OnboardingPreferences.isCompleted(this@MainActivity)

                        if (state == SessionState.EXPIRED && onboardingCompleted) {
                            handleExpiredSession(navController)
                        }
                    }
                }
                launch {
                    userSession.currentUser.collect { user ->
                        val onboardingCompleted = OnboardingPreferences.isCompleted(this@MainActivity)

                        if (user != null && onboardingCompleted) {
                            viewModel.socketConnect()
                            sseManager.connect()
                            viewModel.loadCurrentUserProfile(user.id)

                            if (user.mode == UserMode.PUBLIC) {
                                // PUBLIC là mặc định: khôi phục tracking ngay cả
                                // khi bản cài cũ chưa có cờ local hoặc cờ đang false.
                                // PRIVATE từ backend là lựa chọn tắt của người dùng
                                // và không bị tự động ghi đè.
                                trackingPreferences.setTrackingEnabled(true)
                                val started = LocationTrackingController.start(
                                    this@MainActivity
                                )
                                if (!started) {
                                    trackingPreferences.setTrackingEnabled(false)
                                }
                            } else if (user.mode == UserMode.PRIVATE) {
                                trackingPreferences.setTrackingEnabled(false)
                                LocationTrackingController.stop(this@MainActivity)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleExpiredSession(navController: NavController) {
        trackingPreferences.setTrackingEnabled(false)
        LocationTrackingController.stop(this)
        viewModel.socketDisconnect()
        sseManager.disconnect()

        /*
         * Đặt lại trạng thái để collector không xử lý liên tục.
         */
        userSession.markSessionActive()

        val navOptions = NavOptions.Builder()
            .setPopUpTo(
                R.id.nav_graph,
                inclusive = true
            )
            .build()

        navController.navigate(
            R.id.startFragment,
            null,
            navOptions
        )

        Toast.makeText(
            this,
            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is MainEvent.DetectedNsfw -> {
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle("Bài viết vi phạm")
                                .setMessage(
                                    "Ảnh bạn vừa đăng có thể chứa nội dung nhạy cảm " +
                                            "và không phù hợp với tiêu chuẩn cộng đồng."
                                )
                                .setPositiveButton("Tôi đã hiểu", null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleNavigationIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun sendFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_INIT", "Lấy FCM token thất bại", task.exception)
                return@addOnCompleteListener
            }

            Log.d("FCM_INIT", "FCM token đã được lấy thành công")
        }
    }

    private fun handleNavigationIntent(intent: Intent?) {
        when (intent?.action) {
            WidgetPendingIntentFactory.ACTION_OPEN_POST_REEL -> {
                widgetNavigationViewModel.goToTarget()
                intent.action = null
            }

            LocationTrackingController.ACTION_OPEN_CURRENT_LOCATION -> {
                locationNavigationViewModel.requestCurrentLocationFocus()

                if (
                    userSession.getCurrentUser() != null &&
                    navController.currentDestination?.id != R.id.centerFragment
                ) {
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .setLaunchSingleTop(true)
                        .build()

                    navController.navigate(R.id.centerFragment, null, navOptions)
                }

                // Không xử lý lại cùng action khi Activity được tạo lại do xoay màn hình.
                intent.action = null
            }
        }
    }
}
