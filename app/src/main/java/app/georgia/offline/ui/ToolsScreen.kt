package app.georgia.offline.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import app.georgia.offline.ui.theme.Space
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.domain.GeoFormatting
import java.util.Locale

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, settings: LocalSettings) {
    val context = LocalContext.current
    var gpsActive by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        gpsActive = granted
        gpsError = if (granted) null else "Разрешение на геолокацию не предоставлено. GPS не запущен."
    }

    val hasLocationPermission = hasLocationPermission(context)
    DisposableEffect(gpsActive, hasLocationPermission) {
        if (!gpsActive || !hasLocationPermission) {
            onDispose { }
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location
                    gpsError = null
                }
                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    gpsError = "GPS отключён в настройках устройства."
                }
            }
            try {
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    gpsError = "GPS отключён в настройках устройства."
                } else {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                        currentLocation = it
                    }
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1_000L,
                        1f,
                        listener
                    )
                }
            } catch (_: SecurityException) {
                gpsError = "Android не разрешил доступ к GPS. Проверь разрешения приложения."
                gpsActive = false
            } catch (exception: IllegalArgumentException) {
                gpsError = "На устройстве нет доступного GPS-провайдера: ${exception.localizedMessage.orEmpty()}"
            }
            onDispose {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    val compassHeading = rememberCompassHeading(context)

    PageColumn(modifier) {
        PageTitle("Утилиты", "Локальные датчики телефона. Сетевое подключение не используется.")

        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("GPS и координаты", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        gpsActive && currentLocation != null -> "GPS работает · получены координаты"
                        gpsActive -> "GPS запущен · ожидаем спутниковый сигнал"
                        else -> "GPS выключен"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentLocation != null) {
                    val location = currentLocation!!
                    MetricRow("Широта / долгота", GeoFormatting.decimalCoordinates(location.latitude, location.longitude))
                    MetricRow("Широта DMS", GeoFormatting.degreesMinutesSeconds(location.latitude, latitude = true))
                    MetricRow("Долгота DMS", GeoFormatting.degreesMinutesSeconds(location.longitude, latitude = false))
                    MetricRow("Точность", if (location.hasAccuracy()) "±${location.accuracy.toInt()} м" else "неизвестна")
                    MetricRow("Высота GPS", if (location.hasAltitude()) String.format(Locale.getDefault(), "%.1f м", location.altitude) else "нет данных")
                    MetricRow("Скорость", if (location.hasSpeed()) String.format(Locale.getDefault(), "%.1f км/ч", location.speed * 3.6f) else "нет данных")
                    MetricRow("Направление движения", if (location.hasBearing()) String.format(Locale.getDefault(), "%.0f°", location.bearing) else "нет данных")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        OutlinedButton(onClick = { copyToClipboard(context, GeoFormatting.decimalCoordinates(location.latitude, location.longitude)) }) {
                            Text("Копировать координаты")
                        }
                        Button(onClick = { settings.saveCarLocation(location.latitude, location.longitude) }) {
                            Text("Отметить авто")
                        }
                    }
                } else {
                    Text("Покажи телефону открытое небо. Первый GPS-фикс может занять некоторое время.", style = MaterialTheme.typography.bodySmall)
                }
                if (gpsError != null) {
                    Text(gpsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Button(onClick = {
                        if (gpsActive) {
                            gpsActive = false
                        } else if (hasLocationPermission) {
                            gpsError = null
                            gpsActive = true
                        } else {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    }) {
                        Text(if (gpsActive) "Остановить GPS" else "Включить GPS")
                    }
                    if (currentLocation != null) {
                        OutlinedButton(onClick = { currentLocation = null }) { Text("Очистить экран") }
                    }
                }
                Text("Геолокация запрашивается только после нажатия «Включить GPS». В фоне приложение её не отслеживает.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Компас", style = MaterialTheme.typography.titleMedium)
                if (compassHeading == null) {
                    Text("На устройстве нет датчика ориентации, необходимого для электронного компаса.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(String.format(Locale.getDefault(), "%.0f° · %s", compassHeading, GeoFormatting.cardinalDirection(compassHeading)), style = MaterialTheme.typography.headlineMedium)
                    Text("Показания зависят от датчиков телефона и могут требовать калибровки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Метка автомобиля", style = MaterialTheme.typography.titleMedium)
                if (settings.carLatitude != null && settings.carLongitude != null) {
                    Text(GeoFormatting.decimalCoordinates(settings.carLatitude!!, settings.carLongitude!!))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        OutlinedButton(onClick = { copyToClipboard(context, GeoFormatting.decimalCoordinates(settings.carLatitude!!, settings.carLongitude!!)) }) {
                            Text("Копировать")
                        }
                        OutlinedButton(onClick = settings::clearCarLocation) { Text("Удалить метку") }
                    }
                } else {
                    Text("Метка ещё не сохранена. Включи GPS и нажми «Отметить авто».", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Метка хранится только на этом устройстве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Важное ограничение", style = MaterialTheme.typography.titleMedium)
                Text("Высота берётся из GPS устройства и может заметно ошибаться. Для безопасности сравнивай её с картой высот и не используй телефон как единственный навигационный прибор.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Georgia Offline coordinates", text))
}

@Composable
private fun rememberCompassHeading(context: Context): Float? {
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            heading = null
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientation = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val rawDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    heading = GeoFormatting.normalizeHeading(rawDegrees)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return heading
}
