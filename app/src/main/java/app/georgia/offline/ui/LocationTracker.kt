package app.georgia.offline.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Location updates for the map. Nothing starts until [start] is called from a user action;
 * updates stop when the screen leaves composition. Uses only the system location providers,
 * so no network permission is needed and no coordinates leave the device.
 */
class LocationTracker internal constructor() {
    var active by mutableStateOf(false)
        internal set
    var location by mutableStateOf<Location?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal var hasPermission: () -> Boolean = { false }
    internal var requestPermission: () -> Unit = {}

    fun start() {
        error = null
        if (hasPermission()) active = true else requestPermission()
    }

    fun stop() {
        active = false
    }
}

@Composable
fun rememberLocationTracker(): LocationTracker {
    val context = LocalContext.current
    val tracker = remember { LocationTracker() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        tracker.active = granted
        tracker.error = if (granted) null else "Нет разрешения на геолокацию."
    }
    tracker.hasPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    tracker.requestPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    DisposableEffect(tracker.active) {
        if (!tracker.active) {
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    tracker.location = location
                    tracker.error = null
                }
                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    tracker.error = "Геолокация отключена в настройках телефона."
                }
            }
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            // With only approximate permission (Android 12+) the GPS provider is not allowed.
            val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            try {
                if (!manager.isProviderEnabled(provider)) {
                    tracker.error = "Геолокация отключена в настройках телефона."
                } else {
                    manager.getLastKnownLocation(provider)?.let { tracker.location = it }
                    manager.requestLocationUpdates(provider, 1_000L, 1f, listener)
                }
            } catch (_: SecurityException) {
                tracker.error = "Android не разрешил доступ к геолокации."
                tracker.active = false
            } catch (_: IllegalArgumentException) {
                tracker.error = "На устройстве нет подходящего источника геолокации."
            }
            onDispose { runCatching { manager.removeUpdates(listener) } }
        }
    }
    return tracker
}
