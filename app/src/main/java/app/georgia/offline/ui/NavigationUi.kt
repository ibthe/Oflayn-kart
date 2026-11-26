package app.georgia.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import app.georgia.offline.ui.theme.accentBrush
import app.georgia.offline.ui.theme.edgeBrush
import app.georgia.offline.ui.theme.glass
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.georgia.offline.domain.GeoMath
import app.georgia.offline.routing.Maneuver
import app.georgia.offline.routing.Route
import app.georgia.offline.routing.RouteFailure
import app.georgia.offline.routing.RoutingProfile
import app.georgia.offline.routing.RoutingRepository
import app.georgia.offline.ui.theme.Radius
import app.georgia.offline.ui.theme.Space
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TrackingEvent { NONE, REROUTE, ARRIVED }

/** State of one planned or running road route. Lives with the map screen. */
class RouteSession {
    var profile by mutableStateOf(RoutingProfile.CAR)
    var route by mutableStateOf<Route?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var navigating by mutableStateOf(false)
    var voice by mutableStateOf(true)
    var along by mutableDoubleStateOf(0.0)
    var fitToken by mutableIntStateOf(0)

    private var requestId = 0
    private var segmentHint = 0
    private var offRouteFixes = 0
    private var lastRerouteAt = 0L
    private val announced = HashSet<String>()

    /** Drops the route and cancels any calculation in flight. */
    fun reset() {
        requestId++
        busy = false
        route = null
        message = null
        navigating = false
        along = 0.0
        segmentHint = 0
        offRouteFixes = 0
        announced.clear()
    }

    fun beginNavigation() {
        announced.clear()
        along = 0.0
        segmentHint = 0
        offRouteFixes = 0
        navigating = true
    }

    /** Computes a route from the given position. When [rerouting] the current route stays on screen until the new one is ready. */
    suspend fun plan(
        context: Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        rerouting: Boolean,
        waypoints: List<Pair<Double, Double>> = emptyList()
    ) {
        val id = ++requestId
        busy = true
        if (!rerouting) message = null
        val loaded = withContext(Dispatchers.IO) { RoutingRepository.load(context) }
        val router = loaded.router
        if (id != requestId) return
        if (router == null) {
            message = loaded.error
            busy = false
            return
        }
        val orderedPoints = buildList {
            add(fromLat to fromLon)
            addAll(waypoints)
            add(toLat to toLon)
        }
        val result = withContext(Dispatchers.Default) { router.route(profile, orderedPoints) }
        if (id != requestId) return
        busy = false
        val found = result.route
        if (found != null) {
            route = found
            message = null
            along = 0.0
            segmentHint = 0
            offRouteFixes = 0
            announced.clear()

        } else {
            message = when (result.failure) {
                RouteFailure.NO_ROAD_NEAR_START -> "Рядом с вами нет подходящей дороги для этого способа."
                RouteFailure.NO_ROAD_NEAR_END -> "Рядом с целью нет подходящей дороги для этого способа."
                else -> "Путь не найден: дороги не связаны для этого способа передвижения."
            }
        }
    }

    /** Feeds a GPS fix to the running navigation. */
    fun update(lat: Double, lon: Double, nowMillis: Long): TrackingEvent {
        val current = route ?: return TrackingEvent.NONE
        val projection = current.project(lat, lon, segmentHint)
        segmentHint = projection.segment
        along = projection.alongMeters
        if (current.distanceMeters - along < ARRIVE_METERS) return TrackingEvent.ARRIVED
        if (projection.offRouteMeters > OFF_ROUTE_METERS) {
            offRouteFixes++
            if (offRouteFixes >= 3 && !busy && nowMillis - lastRerouteAt > 10_000L) {
                lastRerouteAt = nowMillis
                offRouteFixes = 0
                return TrackingEvent.REROUTE
            }
        } else {
            offRouteFixes = 0
        }
        return TrackingEvent.NONE
    }

    /** A phrase to speak now, if the next instruction has just come into range and was not announced yet. */
    fun pendingAnnouncement(): String? {
        val current = route ?: return null
        val next = current.nextStep(along) ?: return null
        val index = current.steps.indexOf(next)
        val remaining = next.distanceFromStartMeters - along
        val far = if (profile == RoutingProfile.CAR || profile == RoutingProfile.OFFROAD) 300.0 else 100.0
        val near = if (profile == RoutingProfile.CAR || profile == RoutingProfile.OFFROAD) 50.0 else 20.0
        return when {
            remaining <= near && announced.add("$index:near") -> next.text
            remaining <= far && remaining > near && announced.add("$index:far") ->
                "Через ${GeoMath.formatDistance((remaining / 50.0).toInt() * 50.0 + 50.0)}: ${next.text.replaceFirstChar { it.lowercase() }}"
            else -> null
        }
    }

    private companion object {
        const val OFF_ROUTE_METERS = 70.0
        const val ARRIVE_METERS = 25.0
    }
}

/** Russian text-to-speech for guidance. Silent when the phone has no offline Russian voice. */
class Speaker(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale("ru"))
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String) {
        if (ready) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guidance")
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}

fun formatDuration(seconds: Double): String {
    val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
    return if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
}

private fun maneuverGlyph(maneuver: Maneuver): String = when (maneuver) {
    Maneuver.ARRIVE -> "●"
    Maneuver.WAYPOINT -> "◆"
    else -> "↑"
}

private fun maneuverRotation(maneuver: Maneuver): Float = when (maneuver) {
    Maneuver.WAYPOINT -> 0f
    Maneuver.SLIGHT_LEFT -> -45f
    Maneuver.LEFT -> -90f
    Maneuver.SHARP_LEFT -> -135f
    Maneuver.SLIGHT_RIGHT -> 45f
    Maneuver.RIGHT -> 90f
    Maneuver.SHARP_RIGHT -> 135f
    Maneuver.UTURN -> 180f
    else -> 0f
}

@Composable
private fun profileChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

/** Shown after a destination is chosen: straight-line distance, travel mode, and the route button. */
@Composable
fun RoutePlannerCard(
    target: NavigationTarget,
    origin: NavigationTarget?,
    viaPoints: List<NavigationTarget>,
    onMoveWaypoint: (Int, Int) -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    onSave: (() -> Unit)?,
    location: android.location.Location?,
    session: RouteSession,
    onProfile: (RoutingProfile) -> Unit,
    selectionMode: RoutePointRole?,
    onChooseOrigin: () -> Unit,
    onChooseDestination: () -> Unit,
    onUseMyLocation: () -> Unit,
    onDestinationMyLocation: () -> Unit,
    onSwap: () -> Unit,
    onPlan: () -> Unit,
    onStart: () -> Unit,
    onExternal: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().appear().dismissOnSwipeDown(onCancel),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.glass(app.georgia.offline.ui.theme.LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush()),
        shadowElevation = 16.dp
    ) {
        Column(
            Modifier.padding(Space.m).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            // A visible grab handle makes the downward swipe-to-collapse discoverable.
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .padding(bottom = Space.xxs)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
            )
            val route = session.route
            val startLat = origin?.latitude ?: location?.latitude
            val startLon = origin?.longitude ?: location?.longitude
            val distance = if (startLat != null && startLon != null) {
                GeoMath.distanceMeters(startLat, startLon, target.latitude, target.longitude)
            } else {
                null
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text("ОТКУДА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            origin?.name ?: "Моё местоположение",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    TextButton(onClick = onUseMyLocation, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = Space.s)) {
                        Text("Моё местоположение", maxLines = 1, softWrap = false)
                    }
                    TextButton(onClick = onChooseOrigin, modifier = Modifier.weight(1f).background(if (origin != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(Space.m)), contentPadding = PaddingValues(horizontal = Space.s)) {
                        Text("Точка на карте", maxLines = 1, softWrap = false)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text("КУДА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            target.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    TextButton(
                        onClick = onDestinationMyLocation,
                        enabled = location != null,
                        contentPadding = PaddingValues(horizontal = Space.s)
                    ) {
                        Text("Моё местоположение", maxLines = 1, softWrap = false)
                    }
                    TextButton(onClick = onChooseDestination, modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Space.m)), contentPadding = PaddingValues(horizontal = Space.s)) {
                        Text("Точка на карте", maxLines = 1, softWrap = false)
                    }
                }
                if (origin != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onSwap, contentPadding = PaddingValues(horizontal = Space.s)) {
                            Text("Поменять местами", maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (viaPoints.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text("ПРОМЕЖУТОЧНЫЕ ТОЧКИ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        viaPoints.forEachIndexed { index, point ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
                            ) {
                                Text("${index + 1}.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(point.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { onMoveWaypoint(index, index - 1) }, enabled = index > 0, contentPadding = PaddingValues(horizontal = Space.xxs)) { Text("↑") }
                                TextButton(onClick = { onMoveWaypoint(index, index + 1) }, enabled = index < viaPoints.lastIndex, contentPadding = PaddingValues(horizontal = Space.xxs)) { Text("↓") }
                                TextButton(onClick = { onRemoveWaypoint(index) }, contentPadding = PaddingValues(horizontal = Space.xxs)) { Text("×") }
                            }
                        }
                    }
                }
            }
            if (selectionMode != null) {
                Text(
                    if (selectionMode == RoutePointRole.START)
                        "Удерживайте нужное место на карте, чтобы задать начало маршрута."
                    else
                        "Удерживайте нужное место на карте, чтобы изменить пункт назначения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (route == null) {
                Text(
                    distance?.let { "${GeoMath.formatDistance(it)} по прямой" } ?: "Жду сигнал GPS…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                RoutingProfile.entries.forEach { option ->
                    SelectPill(
                        label = option.label,
                        selected = session.profile == option,
                        onClick = { onProfile(option) },
                        transparentWhenUnselected = true
                    )
                }
            }
            session.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (origin != null && route != null) {
                Text(
                    "Это просмотр маршрута от выбранной точки. Чтобы вести по нему, постройте маршрут от своего положения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode == null) {
                if (route == null) {
                    GradientButton(
                        text = if (session.busy) "Считаю…" else "Построить маршрут",
                        onClick = onPlan,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !session.busy && (origin != null || location != null)
                    )
                } else if (origin == null) {
                    GradientButton(text = "Начать навигацию", onClick = onStart, modifier = Modifier.fillMaxWidth())
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSave != null) {
                    TextButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = Space.xs)
                    ) {
                        Text("Сохранить", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (origin == null && selectionMode == null) {
                    TextButton(
                        onClick = onExternal,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = Space.xs)
                    ) {
                        Text("Навигатор", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = Space.s)
            ) {
                Text("Закрыть")
            }
        }
    }
}

/** Compact route summary anchored below the map controls on the main map screen. */
@Composable
fun RouteSummaryBar(route: Route, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().appear(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.glass(app.georgia.offline.ui.theme.LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush()),
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text("МАРШРУТ ПОСТРОЕН", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${GeoMath.formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Big next-turn banner shown while navigating. */
@Composable
fun NavigationBanner(route: Route, along: Double, modifier: Modifier = Modifier) {
    val next = route.nextStep(along)
    Box(
        modifier = modifier.fillMaxWidth().appear()
            .shadow(16.dp, RoundedCornerShape(Radius.card))
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.accentBrush())
    ) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (next != null) maneuverGlyph(next.maneuver) else "●",
                    modifier = Modifier.rotate(if (next != null) maneuverRotation(next.maneuver) else 0f),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    if (next != null) GeoMath.formatDistance((next.distanceFromStartMeters - along).coerceAtLeast(0.0)) else "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    next?.text ?: "Следуйте по маршруту",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Remaining distance and time with the voice switch and the stop button. */
@Composable
fun NavigationFooter(route: Route, session: RouteSession, onStop: () -> Unit) {
    val remaining = (route.distanceMeters - session.along).coerceAtLeast(0.0)
    val seconds = if (route.distanceMeters > 0) route.durationSeconds * remaining / route.distanceMeters else 0.0
    Surface(
        modifier = Modifier.fillMaxWidth().appear(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.glass(app.georgia.offline.ui.theme.LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush()),
        shadowElevation = 16.dp
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text("${GeoMath.formatDistance(remaining)} · ${formatDuration(seconds)}", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (session.busy) "Пересчитываю маршрут…" else "© OpenStreetMap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { session.voice = !session.voice }, contentPadding = PaddingValues(horizontal = Space.s)) {
                Text(if (session.voice) "Голос: вкл" else "Голос: выкл")
            }
            GradientButton(text = "Завершить", onClick = onStop)
        }
    }
}
