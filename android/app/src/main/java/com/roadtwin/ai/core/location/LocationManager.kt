package com.roadtwin.ai.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.*

private const val TAG = "LocationManager"

class LocationManager(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Checks if location permissions are currently granted.
     */
    fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasFine || hasCoarse
    }

    /**
     * Suspends and fetches the current high-accuracy GPS location.
     * Returns null if permissions are denied or location is unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && (System.currentTimeMillis() - location.time) < 15000) {
                continuation.resume(location)
            } else {
                val locationRequest = LocationRequest.Builder(priority, 1000)
                    .setMaxUpdates(1)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        if (continuation.isActive) {
                            continuation.resume(result.lastLocation)
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }

                continuation.invokeOnCancellation {
                    fusedLocationClient.removeLocationUpdates(callback)
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )
            }
        }.addOnFailureListener {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    /**
     * Emits a reactive stream of continuous location updates for live session distance tracking.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 2000L): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Converts GPS coordinates into a human-readable street/area address using Android's Geocoder.
     * Gracefully falls back to formatted coordinates if geocoding is unavailable or offline.
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) {
            return@withContext "Location unavailable"
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (addresses.isNotEmpty()) {
                                cont.resume(formatAddress(addresses[0], latitude, longitude))
                            } else {
                                cont.resume(formatFallbackCoordinates(latitude, longitude))
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoding error: $errorMessage")
                            cont.resume(formatFallbackCoordinates(latitude, longitude))
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    return@withContext formatAddress(addresses[0], latitude, longitude)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder lookup failed: ${e.message}")
        }

        return@withContext formatFallbackCoordinates(latitude, longitude)
    }

    private fun formatAddress(address: Address, lat: Double, lon: Double): String {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) return line

        val parts = listOfNotNull(
            address.thoroughfare,
            address.subLocality,
            address.locality,
            address.adminArea,
            address.postalCode
        ).filter { it.isNotBlank() }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            formatFallbackCoordinates(lat, lon)
        }
    }

    private fun formatFallbackCoordinates(lat: Double, lon: Double): String {
        return String.format(Locale.US, "Lat: %.5f, Lon: %.5f", lat, lon)
    }

    companion object {
        /**
         * Calculates distance in kilometers between two GPS coordinates using Haversine formula.
         */
        fun calculateDistanceKm(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return 0.0
            val r = 6371.0 // Earth radius in kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
