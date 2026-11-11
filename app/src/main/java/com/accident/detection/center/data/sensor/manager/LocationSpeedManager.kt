package com.accident.detection.center.data.sensor.manager

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Float = 0.0f,
    val accuracyMeters: Float = 0.0f,
    val altitudeMeters: Double = 0.0,
    val bearingDegrees: Float = 0.0f,
    val timestampEpochMs: Long = 0L,
    val hasFix: Boolean = false
)

@Singleton
class LocationSpeedManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow.asStateFlow()

    private var isTracking = false
    private var isHighAccuracyMode = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val speedMps = if (location.hasSpeed()) location.speed else 0.0f
            val speedKmh = speedMps * 3.6f // m/s to km/h

            _locationFlow.value = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = speedKmh,
                accuracyMeters = location.accuracy,
                altitudeMeters = location.altitude,
                bearingDegrees = location.bearing,
                timestampEpochMs = location.time,
                hasFix = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(highAccuracy: Boolean = false) {
        if (!hasLocationPermission()) return

        if (isTracking && isHighAccuracyMode == highAccuracy) {
            return
        }

        stopTracking()
        isTracking = true
        isHighAccuracyMode = highAccuracy

        val intervalMs = if (highAccuracy) 1000L else 5000L
        val minIntervalMs = if (highAccuracy) 500L else 2000L
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            isTracking = false
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

