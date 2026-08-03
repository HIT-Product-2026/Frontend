package com.pando.app.core.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pando.app.MainActivity
import com.pando.app.R
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.network.socket.SocketConnectionManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.home.data.repository.UserRepository
import com.pando.app.features.home.data.socket.MapSocket
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var socketConnectionManager: SocketConnectionManager

    @Inject
    lateinit var mapSocket: MapSocket

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userSession: UserSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sendDisposables = CompositeDisposable()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationUpdatesStarted = false
    private var connectionObserverJob: Job? = null
    private var pendingLocation: Location? = null
    private var isSendingLocation = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_INTERVAL_MILLIS
    )
        .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MILLIS)
        .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            pendingLocation = location
            sendPendingLocationIfPossible()

            Log.d(
                TAG,
                "Location received: lat=${location.latitude}, lng=${location.longitude}"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        observeSocketConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            LocationTrackingController.ACTION_STOP -> stopTrackingByUser()
            LocationTrackingController.ACTION_START, null -> startTrackingIfAllowed()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeLocationUpdates()
        connectionObserverJob?.cancel()
        sendDisposables.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTrackingIfAllowed() {
        if (!trackingPreferences.isTrackingEnabled()) {
            stopSelf()
            return
        }

        if (tokenManager.getAccessToken().isNullOrBlank()) {
            trackingPreferences.setTrackingEnabled(false)
            stopSelf()
            return
        }

        if (!hasLocationPermission()) {
            trackingPreferences.setTrackingEnabled(false)
            stopSelf()
            return
        }

        promoteToForeground()
        socketConnectionManager.connect()
        requestLocationUpdates()
    }

    private fun promoteToForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = LocationTrackingController.ACTION_OPEN_CURRENT_LOCATION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java)
            .setAction(LocationTrackingController.ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_TRACKING_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_point_map)
            .setContentTitle(getString(R.string.location_tracking_notification_title))
            .setContentText(getString(R.string.location_tracking_notification_content))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(R.string.stop_location_sharing),
                stopPendingIntent
            )
            .build()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
    }

    private fun requestLocationUpdates() {
        if (locationUpdatesStarted || !hasLocationPermission()) return

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            locationUpdatesStarted = true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Location permission was revoked", securityException)
            trackingPreferences.setTrackingEnabled(false)
            stopForegroundAndSelf()
        }
    }

    private fun removeLocationUpdates() {
        if (!locationUpdatesStarted) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationUpdatesStarted = false
    }

    private fun observeSocketConnection() {
        connectionObserverJob?.cancel()
        connectionObserverJob = serviceScope.launch {
            socketConnectionManager.connectionState.collectLatest { state ->
                if (state == SocketConnectionState.Connected) {
                    sendPendingLocationIfPossible()
                }
            }
        }
    }

    private fun sendPendingLocationIfPossible() {
        if (isSendingLocation) return
        if (socketConnectionManager.connectionState.value != SocketConnectionState.Connected) {
            return
        }

        val location = pendingLocation ?: return
        val sendOperation = mapSocket.createSendLocationOperation(
            longitude = location.longitude,
            latitude = location.latitude
        ) ?: return

        isSendingLocation = true
        val disposable = sendOperation.subscribe(
            {
                if (pendingLocation === location) {
                    pendingLocation = null
                }
                isSendingLocation = false
                sendPendingLocationIfPossible()
            },
            { throwable ->
                isSendingLocation = false
                Log.e(TAG, "Could not send location; keeping the latest point", throwable)
            }
        )
        sendDisposables.add(disposable)
    }

    private fun stopTrackingByUser() {
        trackingPreferences.setTrackingEnabled(false)
        pendingLocation = null
        removeLocationUpdates()

        // Nút Dừng trên notification không đi qua PrivacyFragment, vì vậy service
        // tự đồng bộ PRIVATE lên backend sau khi đã ngừng GPS trên thiết bị.
        serviceScope.launch {
            when (userRepository.updateUserMode(UserMode.PRIVATE)) {
                is DataResult.Success -> userSession.updateCurrentUser { user ->
                    user.copy(mode = UserMode.PRIVATE)
                }

                is DataResult.Error -> Log.e(
                    TAG,
                    "Stopped local tracking but could not sync PRIVATE mode"
                )
            }

            stopForegroundAndSelf()
        }
    }

    private fun stopForegroundAndSelf() {
        removeLocationUpdates()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.location_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.location_tracking_channel_description)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val OPEN_APP_REQUEST_CODE = 1002
        private const val STOP_TRACKING_REQUEST_CODE = 1003
        private const val LOCATION_INTERVAL_MILLIS = 10_000L
        private const val MIN_LOCATION_INTERVAL_MILLIS = 5_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 10f
    }
}
