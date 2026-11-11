package com.accident.detection.center.dispatch.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.accident.detection.center.sensor.CrashEventPayload
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ResolvedCrashTelemetry(
    val latitude: Double,
    val longitude: Double,
    val streetAddress: String,
    val batteryPercentage: Int,
    val mapsUrl: String,
    val isFreshGpsFix: Boolean
)

/**
 * Resolves high-precision crash telemetry at the instant of emergency dispatch:
 * 1. Queries fresh GPS location with 3-second timeout fallback to payload location.
 * 2. Reverse-geocodes street address supporting API 33+ async and API 26-32 sync APIs.
 * 3. Reads device battery percentage via BatteryManager.
 * 4. Assembles universal Google Maps location URL.
 */
@Singleton
class CrashLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val tag = "CrashLocationResolver"

    @SuppressLint("MissingPermission")
    suspend fun resolveCrashTelemetry(payload: CrashEventPayload): ResolvedCrashTelemetry = withContext(Dispatchers.IO) {
        // 1. Fetch fresh GPS fix (3-second timeout before falling back to pre-impact payload)
        var freshLocation: Location? = null
        try {
            freshLocation = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine { cont ->
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            cont.resume(loc)
                        }
                        .addOnFailureListener {
                            cont.resume(null)
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed or timed out fetching fresh GPS fix: ${e.message}")
        }

        val latitude = freshLocation?.latitude ?: payload.latitude
        val longitude = freshLocation?.longitude ?: payload.longitude
        val isFresh = freshLocation != null

        // 2. Reverse-geocode street address
        val streetAddress = resolveStreetAddress(latitude, longitude)

        // 3. Read current battery percentage
        val batteryPercentage = getBatteryPercentage()

        // 4. Construct Google Maps link
        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"

        ResolvedCrashTelemetry(
            latitude = latitude,
            longitude = longitude,
            streetAddress = streetAddress,
            batteryPercentage = batteryPercentage,
            mapsUrl = mapsUrl,
            isFreshGpsFix = isFresh
        )
    }

    private suspend fun resolveStreetAddress(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) {
            return@withContext "Coordinates Unknown (0.0, 0.0)"
        }

        if (!Geocoder.isPresent()) {
            return@withContext "GPS: %.5f, %.5f (Geocoder unavailable)".format(latitude, longitude)
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            val formatted = addr?.getAddressLine(0)
                                ?: "Near %.5f, %.5f".format(latitude, longitude)
                            continuation.resume(formatted)
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(tag, "Geocode error: $errorMessage")
                            continuation.resume("GPS Coordinates: %.5f, %.5f".format(latitude, longitude))
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val addr = addresses?.firstOrNull()
                addr?.getAddressLine(0) ?: "Near %.5f, %.5f".format(latitude, longitude)
            }
        } catch (e: Exception) {
            Log.w(tag, "Reverse geocoding failed: ${e.message}")
            "GPS Coordinates: %.5f, %.5f".format(latitude, longitude)
        }
    }

    fun getBatteryPercentage(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 0..100) pct else 100
        } catch (e: Exception) {
            100
        }
    }
}

