package com.pando.app.core.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object LocationTrackingController {
    const val ACTION_START = "com.pando.app.location.START"
    const val ACTION_STOP = "com.pando.app.location.STOP"
    const val ACTION_OPEN_CURRENT_LOCATION =
        "com.pando.app.location.OPEN_CURRENT_LOCATION"

    @Volatile
    private var serviceRunning = false

    fun isServiceRunning(): Boolean = serviceRunning

    internal fun markServiceRunning(running: Boolean) {
        serviceRunning = running
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    fun start(context: Context): Boolean {
        if (!hasLocationPermission(context)) return false

        val intent = Intent(
            context,
            LocationTrackingService::class.java
        ).setAction(ACTION_START)

        return runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.isSuccess
    }

    fun stop(context: Context) {
        // Các caller đã lưu trackingEnabled=false trước khi dừng. Dừng trực tiếp
        // tránh race giữa việc gửi ACTION_STOP và stopService ngay sau đó.
        context.stopService(Intent(context, LocationTrackingService::class.java))
    }
}
