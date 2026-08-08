package com.pando.app.features.home.ui.center.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pando.app.core.location.LocationSnapshotStore

class MapLocationController(
    context: Context,
    private val locationSnapshotStore: LocationSnapshotStore,
    private val onLocationUpdate: (Location) -> Unit,
    private val onCapturedLocation: (Location, fromCache: Boolean) -> Unit,
    private val onPermissionDenied: () -> Unit,
    private val onBearingChanged: (Float) -> Unit
) : SensorEventListener {
    private val appContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val locationRequest = LocationRequest
        .Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateIntervalMillis(2_000L)
        .build()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var released = false
    private var lastBearing: Float? = null
    private var lastBearingDispatchAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (released) return
            locationResult.lastLocation?.let { location ->
                locationSnapshotStore.update(location)
                onLocationUpdate(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (released || !hasAnyLocationPermission()) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        if (released) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation() {
        if (released) return

        val fineGranted = hasFineLocationPermission()
        val coarseGranted = hasCoarseLocationPermission()

        if (!fineGranted && !coarseGranted) {
            onPermissionDenied()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
            if (!released) {
                lastLocation?.let {
                    locationSnapshotStore.update(it)
                    onCapturedLocation(it, true)
                }
            }
        }

        fusedLocationClient.getCurrentLocation(
            if (fineGranted) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            },
            null
        ).addOnSuccessListener { location ->
            if (!released) {
                location?.let {
                    locationSnapshotStore.update(it)
                    onCapturedLocation(it, false)
                }
            }
        }
    }

    fun registerBearingUpdates() {
        if (released) return
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun unregisterBearingUpdates() {
        if (released) return
        sensorManager.unregisterListener(this)
    }

    fun hasAnyLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    fun release() {
        if (released) return
        stopLocationUpdates()
        unregisterBearingUpdates()
        released = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (released || event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) {
            return
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        var bearing = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (bearing < 0f) bearing += 360f

        val previousBearing = lastBearing
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = previousBearing?.let { shortestBearingDelta(it, bearing) }
        if (previousBearing == null || (delta != null && delta >= MIN_BEARING_DELTA_DEGREES) ||
            now - lastBearingDispatchAt >= MIN_BEARING_DISPATCH_INTERVAL_MILLIS
        ) {
            lastBearing = bearing
            lastBearingDispatchAt = now
            onBearingChanged(bearing)
        }
    }

    private fun shortestBearingDelta(first: Float, second: Float): Float {
        val difference = kotlin.math.abs(first - second) % 360f
        return minOf(difference, 360f - difference)
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        private const val MIN_BEARING_DELTA_DEGREES = 2f
        private const val MIN_BEARING_DISPATCH_INTERVAL_MILLIS = 150L
    }
}
