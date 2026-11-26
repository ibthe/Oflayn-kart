package app.georgia.offline.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.georgia.offline.ui.theme.Space

/** Which explanatory dialog the location flow wants on screen. */
enum class LocationDialog { RATIONALE, ENABLE_GPS, DENIED }

/**
 * Location updates shared by the whole app. Nothing starts until [start] is called from a user
 * action; updates stop when the app leaves the screen. Uses only the system location providers,
 * so no network permission is needed and no coordinates leave the device.
 */
class LocationTracker internal constructor() {
    var active by mutableStateOf(false)
        internal set
    var location by mutableStateOf<Location?>(null)
        internal set

    /** Last-known fixes are useful while acquiring GPS, but must not be used as a current position. */
    fun hasFreshFix(maxAgeMillis: Long = 30_000L): Boolean {
        val fix = location ?: return false
        val ageNanos = android.os.SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        return fix.elapsedRealtimeNanos > 0L && ageNanos in 0L..(maxAgeMillis * 1_000_000L)
    }

    var error by mutableStateOf<String?>(null)
        internal set

    /** Dialog to show, or null. The dialogs themselves are drawn by [LocationDialogs]. */
    var dialog by mutableStateOf<LocationDialog?>(null)
        internal set

    /** False while the app is in the background: no updates are requested then. */
    internal var foreground by mutableStateOf(true)
    internal var waitingForSystemSettings = false

    internal var hasPermission: () -> Boolean = { false }
    internal var requestPermission: () -> Unit = {}
    internal var providerEnabled: () -> Boolean = { true }

    /** Starts positioning, walking the person through permission and the phone's GPS switch if needed. */
    fun start() {
        error = null
        when {
            !hasPermission() -> {
                active = false
                dialog = LocationDialog.RATIONALE
            }
            !providerEnabled() -> {
                active = false
                dialog = LocationDialog.ENABLE_GPS
            }
            else -> active = true
        }
    }

    fun stop() {
        active = false
    }

    internal fun dismissDialog() {
        dialog = null
        waitingForSystemSettings = false
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun rememberLocationTracker(): LocationTracker {
    val context = LocalContext.current
    val tracker = remember { LocationTracker() }

    fun fineGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun coarseGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    // With only approximate permission (Android 12+) the GPS provider is not allowed.
    fun provider() = if (fineGranted()) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            tracker.dialog = LocationDialog.DENIED
        } else if (!tracker.providerEnabled()) {
            tracker.dialog = LocationDialog.ENABLE_GPS
        } else {
            tracker.error = null
            tracker.active = true
        }
    }
    tracker.hasPermission = { fineGranted() || coarseGranted() }
    tracker.requestPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    tracker.providerEnabled = {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { manager.isProviderEnabled(provider()) }.getOrDefault(false)
    }

    // Pause in the background; when the person comes back from the phone's location settings, carry on.
    DisposableEffect(context) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    tracker.foreground = true
                    if (tracker.waitingForSystemSettings) {
                        tracker.waitingForSystemSettings = false
                        if (tracker.hasPermission() && tracker.providerEnabled()) {
                            tracker.error = null
                            tracker.active = true
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> tracker.foreground = false
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    DisposableEffect(tracker.active, tracker.foreground) {
        if (!tracker.active || !tracker.foreground) {
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
                override fun onProviderEnabled(provider: String) {
                    tracker.error = null
                }
                override fun onProviderDisabled(provider: String) {
                    tracker.active = false
                    tracker.error = "Геолокация отключена в настройках телефона."
                }
            }
            val source = provider()
            try {
                if (!manager.isProviderEnabled(source)) {
                    tracker.error = "Геолокация отключена в настройках телефона."
                    tracker.dialog = LocationDialog.ENABLE_GPS
                    tracker.active = false
                } else {
                    manager.getLastKnownLocation(source)?.let { tracker.location = it }
                    manager.requestLocationUpdates(source, 1_000L, 1f, listener)
                }
            } catch (_: SecurityException) {
                tracker.error = "Android не разрешил доступ к геолокации."
                tracker.active = false
            } catch (_: IllegalArgumentException) {
                tracker.active = false
                tracker.error = "На устройстве нет подходящего источника геолокации."
            }
            onDispose { runCatching { manager.removeUpdates(listener) } }
        }
    }
    return tracker
}

/**
 * The explanations shown before Android's own permission prompt or settings screen. They use the
 * app theme, so the whole flow looks like the rest of the app.
 */
@Composable
fun LocationDialogs(tracker: LocationTracker) {
    val context = LocalContext.current
    val kind = tracker.dialog ?: return
    val scheme = MaterialTheme.colorScheme

    @Composable
    fun Sheet(title: String, body: String, confirm: String, onConfirm: () -> Unit) {
        AlertDialog(
            onDismissRequest = tracker::dismissDialog,
            shape = MaterialTheme.shapes.large,
            containerColor = scheme.surfaceContainerHigh,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                GradientButton(text = confirm, onClick = onConfirm)
            },
            dismissButton = {
                TextButton(onClick = tracker::dismissDialog) { Text("Не сейчас") }
            }
        )
    }

    when (kind) {
        LocationDialog.RATIONALE -> Sheet(
            title = "Определить, где вы находитесь?",
            body = "Приложению нужен доступ к геолокации, чтобы показать вас на карте и построить маршрут. " +
                "Координаты остаются на телефоне и никуда не отправляются, в фоне положение не отслеживается.",
            confirm = "Разрешить"
        ) {
            tracker.dismissDialog()
            tracker.requestPermission()
        }
        LocationDialog.ENABLE_GPS -> Sheet(
            title = "GPS выключен",
            body = "Включите геолокацию в настройках телефона и вернитесь в приложение: определение начнётся само.",
            confirm = "Открыть настройки"
        ) {
            tracker.dismissDialog()
            tracker.waitingForSystemSettings = true
            runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        LocationDialog.DENIED -> Sheet(
            title = "Нет доступа к геолокации",
            body = "Без разрешения положение на карте и маршруты от вашей точки недоступны. Разрешение можно выдать в настройках приложения.",
            confirm = "Настройки приложения"
        ) {
            tracker.dismissDialog()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
