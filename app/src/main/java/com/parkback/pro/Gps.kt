package com.parkback.pro

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.located() =
    granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Location.recent() =
    (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) in 0..30_000_000_000L &&
        validCoordinates(latitude, longitude)

@SuppressLint("MissingPermission")
class Gps(private val context: Context) {
    private val provider = LocationServices.getFusedLocationProviderClient(context)

    fun enabled() = LocationManagerCompat.isLocationEnabled(context.service<LocationManager>())

    suspend fun fix(): Location {
        check(context.located()) { "Allow location access to save a spot." }
        check(enabled()) { "Device location is off. Turn it on and try again." }
        val cancel = CancellationTokenSource()
        val request =
            CurrentLocationRequest.Builder()
                .setMaxUpdateAgeMillis(0)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(20_000)
                .build()
        val result =
            try {
                withTimeoutOrNull(22_000) {
                    provider.getCurrentLocation(request, cancel.token).await()
                }
            } finally {
                cancel.cancel()
            }
        return result?.takeIf { it.recent() }
            ?: error("No fresh location fix. Move toward open sky and retry.")
    }

    fun watch() =
        callbackFlow<Location> {
            if (!context.located() || !enabled()) {
                close()
                return@callbackFlow
            }
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.takeIf { it.recent() }?.let { trySend(it) }
                    }
                }
            val request =
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 6000L)
                    .setMinUpdateIntervalMillis(3000)
                    .setMaxUpdateAgeMillis(0)
                    .build()
            provider
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(it) }
            awaitClose { provider.removeLocationUpdates(callback) }
        }
}
