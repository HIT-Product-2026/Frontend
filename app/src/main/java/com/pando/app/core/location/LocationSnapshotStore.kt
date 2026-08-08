package com.pando.app.core.location

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The latest location shared by foreground map, background tracking and the
 * camera.  It is intentionally process-local: a media upload should never
 * silently use a location persisted from a previous app run.
 */
@Singleton
class LocationSnapshotStore @Inject constructor() {
    private val _snapshot = MutableStateFlow<LocationSnapshot?>(null)
    val snapshot: StateFlow<LocationSnapshot?> = _snapshot.asStateFlow()

    @Synchronized
    fun update(location: Location) {
        _snapshot.value = LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { it > 0f },
            capturedAtElapsedMillis = SystemClock.elapsedRealtime()
        )
    }

    fun latest(): LocationSnapshot? = snapshot.value

    fun fresh(maxAgeMillis: Long): LocationSnapshot? {
        val value = snapshot.value ?: return null
        val age = SystemClock.elapsedRealtime() - value.capturedAtElapsedMillis
        return value.takeIf { age in 0..maxAgeMillis }
    }
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtElapsedMillis: Long
)
