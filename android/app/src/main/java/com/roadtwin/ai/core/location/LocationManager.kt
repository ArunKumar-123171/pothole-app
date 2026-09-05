package com.roadtwin.ai.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
     * Checks if Android system Location Services (GPS or Network provider) are enabled.
     */
    fun isLocationServicesEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return false
        return try {
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (e: Exception) {
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Checks if Google Play Services location settings satisfy PRIORITY_HIGH_ACCURACY.
     * If resolution is required, invokes the system resolution dialog on the given activity.
     */
    fun requestLocationEnable(
        activity: android.app.Activity,
        requestCode: Int = 1001,
        onSatisfied: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d(TAG, "Location settings satisfied")
            onSatisfied()
        }.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                try {
                    Log.d(TAG, "Location resolution required, starting resolution dialog")
                    exception.startResolutionForResult(activity, requestCode)
                } catch (sendEx: Exception) {
                    Log.w(TAG, "Failed to start resolution for result: ${sendEx.message}, opening settings directly")
                    openLocationSettings(activity)
                    onFailed()
                }
            } else {
                Log.w(TAG, "Location settings check failed, launching system location settings")
                openLocationSettings(activity)
                onFailed()
            }
        }
    }

    /**
     * Retrieves the most recent cached location if available.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission() || !isLocationServicesEnabled()) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val gpsLoc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            val netLoc = lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Suspends and fetches the current high-accuracy GPS location with a strict timeout.
     * Returns null if permissions are denied or location is unavailable (never hangs).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission() || !isLocationServicesEnabled()) {
            return null
        }
        val cached = getLastKnownLocation()
        if (cached != null && (System.currentTimeMillis() - cached.time) < 10000) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine { continuation ->
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null && (System.currentTimeMillis() - location.time) < 15000) {
                            if (continuation.isActive) continuation.resume(location)
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
            } ?: cached
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
        return if (lat != 0.0 || lon != 0.0) "GPS location recorded" else "Location unavailable"
    }

    companion object {
        /**
         * Safely launches Android Location Settings screen.
         */
        fun openLocationSettings(ctx: Context) {
            try {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open location settings: ${e.message}")
            }
        }

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
