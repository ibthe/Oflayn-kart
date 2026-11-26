package app.georgia.offline.ui

import android.content.Context
import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.Qibla
import app.georgia.offline.ui.theme.Space
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, settings: LocalSettings, tracker: LocationTracker) {
    val context = LocalContext.current
    val currentLocation = tracker.location?.takeIf { tracker.hasFreshFix() }
    val compass = rememberCompass(currentLocation)
    val heading = compass.heading
    val qiblaBearing = currentLocation?.let { Qibla.bearing(it.latitude, it.longitude).toFloat() }

    PageColumn(modifier) {
        PageTitle("Утилиты", "Датчики телефона. Всё работает без интернета.")

        GpsPanel(settings, tracker, currentLocation, context)

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
