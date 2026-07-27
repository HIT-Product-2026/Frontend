package com.pando.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.session.SessionStartupManager
import com.pando.app.core.session.SessionState
import com.pando.app.core.session.StartupSessionResult
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.ActivityMainBinding
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
    lateinit var socketConnectionManager: SocketConnectionManager
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.FragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        sendFCMToken()

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val result = sessionStartupManager.resolveSession()

                val navGraph = navController.navInflater
                    .inflate(R.navigation.nav_graph)

                val startDestination = when (result) {
                    StartupSessionResult.AUTHENTICATED -> {
                        R.id.centerFragment
                    }

                    StartupSessionResult.NO_SESSION,
                    StartupSessionResult.SESSION_EXPIRED,
                    StartupSessionResult.NETWORK_ERROR -> {
                        R.id.startFragment
                    }
                }

                navGraph.setStartDestination(startDestination)
                navController.graph = navGraph

                when (result) {
                    StartupSessionResult.SESSION_EXPIRED -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    StartupSessionResult.NETWORK_ERROR -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Không thể kiểm tra phiên đăng nhập. Vui lòng kiểm tra kết nối mạng.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> Unit
                }

                observeSessionState(navController)
            }
        } else {
            observeSessionState(navController)
        }
    }

    private fun observeSessionState(navController: NavController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.sessionState.collect { state ->
                    if (state == SessionState.EXPIRED) {
                        socketConnectionManager.disconnect()
                        userSession.markSessionActive()

                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()

                        navController.navigate(R.id.startFragment, null, navOptions)

                        Toast.makeText(
                            this@MainActivity,
                            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sendFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_INIT", "Lấy FCM token thất bại", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM_INIT", "FCM Token của tui là: $token")
        }
    }
}