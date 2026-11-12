package com.accident.detection.center.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class GpsVelocitySnapshot(
    val speedKmh: Float = 0.0f,
    val speedMps: Float = 0.0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val bearingDegrees: Float = 0.0f,
    val horizontalAccuracyMeters: Float = 0.0f,
    val timestampEpochMs: Long = 0L,
    val hasValidFix: Boolean = false
)

@Singleton
class LocationVelocityCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _latestLocationState = MutableStateFlow(GpsVelocitySnapshot())
    val latestLocationState: StateFlow<GpsVelocitySnapshot> = _latestLocationState.asStateFlow()

    companion object {
        private const val MPS_TO_KMH = 3.6f
    }

    /**
     * Flow streaming real-time location and speed updates.
     */
    @SuppressLint("MissingPermission")
    fun locationFlow(
        highAccuracy: Boolean = true,
        intervalMs: Long = 1000L
    ): Flow<GpsVelocitySnapshot> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        // Seed immediately with last known location for zero latency
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val speedMps = if (loc.hasSpeed()) loc.speed else 0.0f
                    val speedKmh = speedMps * MPS_TO_KMH
                    val snapshot = GpsVelocitySnapshot(
                        speedKmh = speedKmh,
                        speedMps = speedMps,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitudeMeters = loc.altitude,
                        bearingDegrees = loc.bearing,
                        horizontalAccuracyMeters = loc.accuracy,
                        timestampEpochMs = loc.time,
                        hasValidFix = true
                    )
                    _latestLocationState.value = snapshot
                    trySend(snapshot)
                }
            }
        } catch (e: SecurityException) {
            // Ignored
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speedMps = if (loc.hasSpeed()) loc.speed else 0.0f
                val speedKmh = speedMps * MPS_TO_KMH

                val snapshot = GpsVelocitySnapshot(
                    speedKmh = speedKmh,
                    speedMps = speedMps,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude,
                    bearingDegrees = loc.bearing,
                    horizontalAccuracyMeters = loc.accuracy,
                    timestampEpochMs = loc.time,
                    hasValidFix = true
                )
                _latestLocationState.value = snapshot
                trySend(snapshot)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

