package com.pando.app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.pando.app.core.data.local.AuthPreferences
import com.pando.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_INIT", "Lấy FCM token thất bại", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM_INIT", "FCM Token của tui là: $token")
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.FragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        val authPreferences = AuthPreferences(this)

        if (authPreferences.isLoggedIn()) {
            navGraph.setStartDestination(R.id.cameraFragment)
        } else {
            navGraph.setStartDestination(R.id.startFragment)
        }

        navController.graph = navGraph
    }
}