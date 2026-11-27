package app.georgia.offline.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.location.Location
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.data.SafetyChecklistRepository
import app.georgia.offline.data.GpxCodec
import app.georgia.offline.data.TrackRepository
import app.georgia.offline.data.SavedTrackInfo
import app.georgia.offline.data.TrackPoint
import app.georgia.offline.data.GpxTrack
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.Qibla
import app.georgia.offline.ui.theme.Space
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale


private data class DeviceStatus(
    val batteryPercent: Int?,
    val charging: Boolean,
    val internetAvailable: Boolean?,
)

private fun readDeviceStatus(context: Context): DeviceStatus {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val fallbackCapacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val percent = when {
        level >= 0 && scale > 0 -> (level * 100 / scale).coerceIn(0, 100)
        fallbackCapacity in 0..100 -> fallbackCapacity
        else -> null
    }
    val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
        batteryStatus == BatteryManager.BATTERY_STATUS_FULL
    val internetAvailable = runCatching {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrNull()
    return DeviceStatus(percent, charging, internetAvailable)
}

@Composable
private fun DeviceStatusPanel(context: Context) {
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    Panel(padding = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Состояние устройства", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { status = readDeviceStatus(context) }) {
                    Text("Обновить")
                }
            }
            MetricRow(
                "Заряд батареи",
                status.batteryPercent?.let { "${it}%" } ?: "нет данных"
            )
            MetricRow("Питание", if (status.charging) "зарядка" else "от батареи")
            MetricRow(
                "Сеть",
                when (status.internetAvailable) {
                    true -> "интернет доступен"
                    false -> "нет сети · офлайн-карты доступны"
                    null -> "статус недоступен"
                }
            )
            if (status.batteryPercent != null && status.batteryPercent <= 20 && !status.charging) {
                Text(
                    "Низкий заряд. Для длительной поездки подключите питание или включите энергосбережение.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Проверка выполняется на устройстве вручную. Приложение не запрашивает доступ к интернету.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PreRideSafetyChecklist(context: Context) {
    val items = SafetyChecklistRepository.items
    var checkedIds by remember { mutableStateOf(SafetyChecklistRepository.load(context)) }

    Panel(padding = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Проверка перед поездкой", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${checkedIds.size} из ${items.size} пунктов отмечено · хранится только на устройстве",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        checkedIds = emptySet()
                        SafetyChecklistRepository.clear(context)
                    },
                    enabled = checkedIds.isNotEmpty()
                ) {
                    Text("Сбросить")
                }
            }
            items.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.id in checkedIds,
                        onCheckedChange = { isChecked ->
                            checkedIds = if (isChecked) checkedIds + item.id else checkedIds - item.id
                            SafetyChecklistRepository.save(context, checkedIds)
                        }
                    )
                    Column(
                        Modifier.weight(1f).padding(vertical = Space.xs),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != items.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, settings: LocalSettings, tracker: LocationTracker, trackRecorder: TrackRecorder, trackRepository: TrackRepository, recoveryLoaded: Boolean, onShowTrack: (List<TrackPoint>) -> Unit, onFollowTrack: (GpxTrack) -> Unit) {
    val context = LocalContext.current
    val currentLocation = tracker.location?.takeIf { tracker.hasFreshFix() }
    val compass = rememberCompass(currentLocation)
    val heading = compass.heading
    val qiblaBearing = currentLocation?.let { Qibla.bearing(it.latitude, it.longitude).toFloat() }

    PageColumn(modifier) {
        PageTitle("Утилиты", "Датчики телефона. Всё работает без интернета.")

        EmergencyPanel(context, currentLocation)
        PreRideSafetyChecklist(context)
        DeviceStatusPanel(context)
        GpsPanel(settings, tracker, currentLocation, context)
        TrackRecorderPanel(context, tracker, trackRecorder, trackRepository, recoveryLoaded, onShowTrack, onFollowTrack)

        // ----- Compass -----
        Panel(padding = 0.dp) {
            Column(
                Modifier.fillMaxWidth().padding(Space.card),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Компас", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Text(
                    "Перед использованием проверьте калибровку: плавно проведите телефоном несколько раз по траектории «восьмёрки». Держите его подальше от магнитов, металлических предметов и магнитных чехлов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (heading == null) {
                    Text(
                        "Не найден датчик поворота или пара акселерометр + магнитометр, необходимые для электронного компаса.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CompassDial(
                        unwrappedHeading = compass.unwrapped,
                        heading = heading,
                        qiblaBearing = qiblaBearing
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f° · %s", heading, GeoFormatting.cardinalDirection(heading)),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        if (compass.trueNorth) {
                            "Истинный север: поправка на магнитное склонение взята по вашему положению."
                        } else {
                            "Магнитный север. Включите GPS, чтобы учесть магнитное склонение (в Грузии около 6° к востоку)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (compass.needsCalibration) {
                        Text(
                            "Датчик просит калибровки: покачайте телефон в воздухе «восьмёркой» и отойдите от магнитов и металла.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ----- Qibla -----
        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Кибла", style = MaterialTheme.typography.titleMedium)
                if (currentLocation == null || qiblaBearing == null) {
                    Text(
                        "Чтобы указать направление на Каабу в Мекке, нужны ваши координаты. Определение работает на телефоне и без интернета.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradientButton(
                        text = if (tracker.active) "Ищу сигнал GPS…" else "Определить положение",
                        onClick = { if (!tracker.active) tracker.start() },
                        enabled = !tracker.active
                    )
                } else {
                    val distance = Qibla.distanceKm(currentLocation.latitude, currentLocation.longitude)
                    Text(
                        String.format(Locale.getDefault(), "%.0f° · %s", qiblaBearing, GeoFormatting.cardinalDirection(qiblaBearing)),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    MetricRow("До Каабы", String.format(Locale.getDefault(), "%,.0f км", distance))
                    if (heading != null) {
                        val delta = Qibla.angleDifference(heading.toDouble(), qiblaBearing.toDouble())
                        val aligned = abs(delta) < 3.0
                        Text(
                            when {
                                aligned -> "Вы смотрите на Каабу ✓"
                                delta > 0 -> "Повернитесь вправо на ${delta.roundToInt()}°"
                                else -> "Повернитесь влево на ${(-delta).roundToInt()}°"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (aligned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Значок Каабы на циферблате компаса показывает направление. Направление вычислено по кратчайшей дуге (ортодромии) от вашего места.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ----- Car mark -----
        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Метка автомобиля", style = MaterialTheme.typography.titleMedium)
                val carLatitude = settings.carLatitude
                val carLongitude = settings.carLongitude
                if (carLatitude != null && carLongitude != null) {
                    Text(GeoFormatting.decimalCoordinates(carLatitude, carLongitude))
                    if (currentLocation != null) {
                        Text(
                            "До автомобиля ${app.georgia.offline.domain.GeoMath.formatDistance(
                                app.georgia.offline.domain.GeoMath.distanceMeters(currentLocation.latitude, currentLocation.longitude, carLatitude, carLongitude)
                            )} по прямой",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        OutlinedButton(onClick = { copyToClipboard(context, GeoFormatting.decimalCoordinates(carLatitude, carLongitude)) }) {
                            Text("Копировать")
                        }
                        OutlinedButton(onClick = settings::clearCarLocation) { Text("Удалить метку") }
                    }
                } else {
                    Text("Метка ещё не сохранена. Включите GPS и нажмите «Отметить авто».", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Метка хранится только на этом устройстве и видна на карте. Нажмите на неё на карте, чтобы построить маршрут к машине.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Важное ограничение", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Высота берётся из GPS устройства и может заметно ошибаться. Компас зависит от магнитных помех. Не используйте телефон как единственный навигационный прибор.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TrackRecorderPanel(context: Context, tracker: LocationTracker, recorder: TrackRecorder, repository: TrackRepository, recoveryLoaded: Boolean, onShowTrack: (List<TrackPoint>) -> Unit, onFollowTrack: (GpxTrack) -> Unit) {
    val scope = rememberCoroutineScope()
    var savedTracks by remember { mutableStateOf(emptyList<SavedTrackInfo>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingExportId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        savedTracks = withContext(Dispatchers.IO) { repository.list() }
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val id = pendingExportId
        pendingExportId = null
        if (uri != null && id != null) {
            scope.launch {
                try {
                    val xml = withContext(Dispatchers.IO) { GpxCodec.write(repository.load(id)) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
                            ?: error("Не удалось создать GPX-файл.")
                    }
                    message = "GPX экспортирован."
                } catch (error: Exception) {
                    message = error.message ?: "Не удалось экспортировать GPX."
                }
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            repository.save(GpxCodec.parse(input))
                        } ?: error("Не удалось открыть выбранный файл.")
                    }
                    savedTracks = withContext(Dispatchers.IO) { repository.list() }
                    message = "GPX импортирован в локальные треки."
                } catch (error: Exception) {
                    message = error.message ?: "Не удалось импортировать GPX."
                }
            }
        }
    }

    Panel(padding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Запись и GPX-треки", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = recorder.note,
                onValueChange = { recorder.note = it.take(1_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Заметка к треку") },
                maxLines = 2,
                singleLine = false
            )
            Text(
                if (recorder.recording) {
                    "Запись активна · точек: ${recorder.points.size}"
                } else {
                    "Записывайте маршрут по GPS и храните GPX-файлы только на этом устройстве."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!recoveryLoaded) {
                Text("Проверяю незавершённую запись…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (recorder.hasRecovery && !recorder.recording) {
                Text(
                    "Найдена незавершённая запись · ${recorder.points.size} точек. Можно продолжить или сохранить её.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    GradientButton(
                        text = "Продолжить",
                        onClick = {
                            if (!tracker.hasPermission() || !tracker.providerEnabled()) {
                                tracker.start()
                                message = "После разрешения геолокации нажмите «Продолжить» ещё раз."
                            } else {
                                tracker.start()
                                recorder.start()
                                message = "Запись восстановлена и продолжается."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val recovered = recorder.finish()
                            if (recovered != null) {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            repository.save(recovered)
                                            repository.clearRecovery()
                                        }
                                        savedTracks = withContext(Dispatchers.IO) { repository.list() }
                                        message = "Восстановленный трек сохранён."
                                    } catch (error: Exception) {
                                        recorder.restore(recovered)
                                        message = error.message ?: "Не удалось сохранить восстановленный трек."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        recorder.cancel()
                        scope.launch { withContext(Dispatchers.IO) { repository.clearRecovery() } }
                        message = "Незавершённый трек удалён."
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Удалить запись") }
            } else if (recorder.recording) {
                GradientButton(
                    text = "Завершить и сохранить трек",
                    onClick = {
                        val track = recorder.finish()
                        if (track == null) {
                            message = "Недостаточно точек GPS: нужно не менее двух."
                        } else {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.save(track)
                                        repository.clearRecovery()
                                    }
                                    savedTracks = withContext(Dispatchers.IO) { repository.list() }
                                    message = "Трек сохранён на устройстве."
                                } catch (error: Exception) {
                                    recorder.restore(track)
                                    message = error.message ?: "Не удалось сохранить трек."
                                }
                            }
                        }
                    }
                )
            } else {
                GradientButton(
                    text = if (!tracker.active) "Начать запись · включить GPS" else "Начать запись трека",
                    enabled = recoveryLoaded,
                    onClick = {
                        if (!tracker.hasPermission() || !tracker.providerEnabled()) {
                            tracker.start()
                            message = "После разрешения геолокации нажмите «Начать запись» ещё раз."
                        } else {
                            tracker.start()
                            recorder.start()
                            message = "Запись начнётся после получения точного GPS-сигнала."
                        }
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml")) }) {
                    Text("Импорт GPX")
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (savedTracks.isNotEmpty()) {
                Text("Сохранённые треки", style = MaterialTheme.typography.titleSmall)
                savedTracks.forEach { track ->
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(track.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            track.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            Text("${track.pointCount} точек", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val gpx = withContext(Dispatchers.IO) { repository.load(track.id) }
                                            val segments = gpx.segments.filter { it.size >= 2 }
                                            val longest = segments.maxByOrNull { it.size }
                                                ?: error("В треке нет непрерывного участка с двумя точками.")
                                            onShowTrack(longest)
                                            message = if (segments.size > 1) {
                                                "На карте показан самый длинный непрерывный сегмент трека."
                                            } else {
                                                "Трек показан на карте."
                                            }
                                        } catch (error: Exception) {
                                            message = error.message ?: "Не удалось открыть трек."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("На карте", maxLines = 1) }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val gpx = withContext(Dispatchers.IO) { repository.load(track.id) }
                                            onFollowTrack(gpx)
                                        } catch (error: Exception) {
                                            message = error.message ?: "Не удалось открыть GPX-трек."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Возврат", maxLines = 1) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    pendingExportId = track.id
                                    exportLauncher.launch("${track.name.replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_").take(40)}.gpx")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Экспорт", maxLines = 1) }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { repository.delete(track.id) }
                                            savedTracks = withContext(Dispatchers.IO) { repository.list() }
                                            message = "Трек удалён."
                                        } catch (error: Exception) {
                                            message = error.message ?: "Не удалось удалить трек."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Удалить", maxLines = 1) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Text(
                "Запись работает только пока приложение открыто. При сворачивании приложения GPS обновления приостанавливаются; фоновое отслеживание не используется.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GpsPanel(settings: LocalSettings, tracker: LocationTracker, location: Location?, context: Context) {
    Panel(padding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                val dot = when {
                    tracker.error != null -> MaterialTheme.colorScheme.error
                    tracker.active && location != null -> MaterialTheme.colorScheme.tertiary
                    tracker.active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
                if (tracker.active && location != null) {
                    PulseDot(dot)
                } else {
                    androidx.compose.foundation.layout.Box(Modifier.padding(6.dp).size(8.dp).background(dot, CircleShape))
                }
                Text("GPS и координаты", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                when {
                    tracker.active && location != null -> "GPS работает · координаты получены"
                    tracker.active -> "GPS запущен · ждём сигнал спутников. Выйдите на открытое место."
                    else -> "GPS выключен"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (location != null) {
                MetricRow("Широта / долгота", GeoFormatting.decimalCoordinates(location.latitude, location.longitude))
                MetricRow("Широта DMS", GeoFormatting.degreesMinutesSeconds(location.latitude, latitude = true))
                MetricRow("Долгота DMS", GeoFormatting.degreesMinutesSeconds(location.longitude, latitude = false))
                MetricRow("Точность", if (location.hasAccuracy()) "±${location.accuracy.toInt()} м" else "неизвестна")
                MetricRow("Высота GPS", if (location.hasAltitude()) String.format(Locale.getDefault(), "%.1f м", location.altitude) else "нет данных")
                MetricRow("Скорость", if (location.hasSpeed()) String.format(Locale.getDefault(), "%.1f км/ч", location.speed * 3.6f) else "нет данных")
                MetricRow("Направление движения", if (location.hasBearing()) String.format(Locale.getDefault(), "%.0f°", location.bearing) else "нет данных")
                val coordinates = GeoFormatting.decimalCoordinates(location.latitude, location.longitude)
                val alreadySaved = settings.savedPlaces.any {
                    kotlin.math.abs(it.latitude - location.latitude) < 1e-5 &&
                        kotlin.math.abs(it.longitude - location.longitude) < 1e-5
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoordinateActionButton(
                        label = "Копировать",
                        successLabel = "Скопировано",
                        modifier = Modifier.weight(1f)
                    ) { copyToClipboard(context, coordinates) }
                    CoordinateActionButton(
                        label = if (alreadySaved) "Уже сохранено" else "Сохранить",
                        successLabel = "Сохранено",
                        initiallyDone = alreadySaved,
                        modifier = Modifier.weight(1f),
                        enabled = !alreadySaved
                    ) { settings.addPlace("Моя позиция", location.latitude, location.longitude) }
                }
                GradientButton(text = "Отметить авто", onClick = { settings.saveCarLocation(location.latitude, location.longitude) })
            }
            tracker.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (tracker.active) {
                OutlinedButton(onClick = tracker::stop) { Text("Остановить GPS") }
            } else {
                GradientButton(text = "Включить GPS", onClick = tracker::start)
            }
            Text(
                "Геолокация запрашивается только после нажатия «Включить GPS» и не отслеживается, когда приложение свёрнуто.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoordinateActionButton(
    label: String,
    successLabel: String,
    modifier: Modifier = Modifier,
    initiallyDone: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var done by remember(label, initiallyDone) { mutableStateOf(initiallyDone) }
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        label = "coordinateActionContainer"
    )
    val content by androidx.compose.animation.animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
        label = "coordinateActionContent"
    )
    OutlinedButton(
        onClick = {
            onClick()
            done = true
        },
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        )
    ) {
        Text(
            if (done) "✓ $successLabel" else label,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Georgia Offline coordinates", text))
}
