package app.georgia.offline.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.georgia.offline.ui.theme.accentBrush
import app.georgia.offline.ui.theme.edgeBrush
import app.georgia.offline.ui.theme.glass
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import app.georgia.offline.domain.GeoMath
import org.mapsforge.core.model.LatLong
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.data.MapFileRepository
import app.georgia.offline.data.PoiRepository
import app.georgia.offline.data.TrackRepository
import app.georgia.offline.data.TripPlanRepository
import app.georgia.offline.routing.TrackRouteBuilder
import app.georgia.offline.domain.PoiLabels
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.MapLanguageResolver
import app.georgia.offline.ui.theme.AppPalette
import app.georgia.offline.ui.theme.AppPalettes
import app.georgia.offline.ui.theme.GeorgiaTheme
import app.georgia.offline.ui.theme.Radius
import app.georgia.offline.ui.theme.Space
import app.georgia.offline.ui.theme.LocalLayerAlpha
import app.georgia.offline.ui.theme.THEME_MODE_CHOICES
import app.georgia.offline.ui.theme.ThemeMode
import app.georgia.offline.ui.theme.paletteById
import app.georgia.offline.ui.theme.resolveThemeMode
import androidx.compose.material3.Slider

private enum class AppTab(val label: String) {
    MAP("Карта"),
    DISCOVER("Места"),
    TOOLS("Утилиты"),
    SETTINGS("Ещё")
}

@Composable
fun GeorgiaOfflineApp(settings: LocalSettings) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.MAP.name) }
    val navigation = remember { NavigationState() }
    val tracker = rememberLocationTracker()
    val trackRecorder = remember { TrackRecorder() }
    val trackRepository = remember(context) { TrackRepository(File(context.filesDir, "tracks")) }
    val tripPlanRepository = remember(context) { TripPlanRepository(File(context.filesDir, "trip-plan.tsv")) }
    var tripPlanRevision by remember { mutableStateOf(0) }
    val tripPlanIds = remember(tripPlanRevision) { tripPlanRepository.list().map { it.poiId }.toSet() }
    var recoveryLoaded by remember { mutableStateOf(false) }
    var selectedTrackPoints by remember { mutableStateOf<List<LatLong>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val recovery = withContext(Dispatchers.IO) { trackRepository.loadRecovery() }
        if (recovery != null) trackRecorder.restore(recovery)
        recoveryLoaded = true
    }
    // Recording follows fresh foreground GPS fixes and survives tab switches.
    val fix = tracker.location?.takeIf { tracker.hasFreshFix() }
    LaunchedEffect(fix, trackRecorder.recording) {
        if (trackRecorder.recording && fix != null) {
            trackRecorder.addFix(fix)
            trackRecorder.checkpointIfDue(fix.elapsedRealtimeNanos)?.let { checkpoint ->
                val generation = trackRecorder.checkpointGeneration
                coroutineScope.launch(Dispatchers.IO) {
                    trackRepository.saveRecovery(checkpoint) {
                        trackRecorder.checkpointGeneration == generation
                    }
                }
            }
        }
    }
    // Route state and its lifecycle belong to the app, not to the currently selected tab.
    val routeSession = remember { RouteSession() }
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val hostView = LocalView.current
    DisposableEffect(routeSession.navigating) {
        hostView.keepScreenOn = routeSession.navigating
        onDispose { hostView.keepScreenOn = false }
    }
    LaunchedEffect(navigation.target, navigation.origin, navigation.viaPoints.toList()) {
        if (!routeSession.followingTrack || navigation.target != null || navigation.origin != null || navigation.viaPoints.isNotEmpty()) {
            routeSession.reset()
        }
    }

    // Keep GPS progress, voice prompts, arrival detection and rerouting alive across tab changes.
    LaunchedEffect(fix, routeSession.navigating, navigation.target, routeSession.followingTrack) {
        val target = navigation.target
        if (!routeSession.navigating || fix == null) return@LaunchedEffect
        if (!routeSession.followingTrack && target == null) return@LaunchedEffect
        when (routeSession.update(fix.latitude, fix.longitude, System.currentTimeMillis())) {
            TrackingEvent.ARRIVED -> {
                val announcement = if (routeSession.followingTrack) "Вы вернулись к началу записанного трека" else "Вы прибыли в пункт назначения"
                if (routeSession.voice) speaker.speak(announcement)
                android.widget.Toast.makeText(context, announcement, android.widget.Toast.LENGTH_LONG).show()
                navigation.clearRoute()
                routeSession.reset()
            }
            TrackingEvent.OFF_TRACK -> {
                android.widget.Toast.makeText(context, "Вы отклонились от записанного трека", android.widget.Toast.LENGTH_SHORT).show()
            }
            TrackingEvent.REROUTE -> {
                // Rerouting always starts at the latest GPS fix, never the old
                // explicitly selected origin. Keep only via stops still ahead.
                val destination = target ?: return@LaunchedEffect
                val via = routeSession.remainingWaypoints()
                coroutineScope.launch {
                    routeSession.plan(
                        context,
                        fix.latitude,
                        fix.longitude,
                        destination.latitude,
                        destination.longitude,
                        rerouting = true,
                        waypoints = via
                    )
                    if (routeSession.navigating && routeSession.voice && routeSession.message == null) {
                        speaker.speak("Маршрут перестроен")
                    }
                }
            }
            TrackingEvent.NONE -> routeSession.pendingAnnouncement()?.let {
                if (routeSession.voice) speaker.speak(it)
            }
        }
    }

    // A tab name saved by an older version (for example the removed ROUTE tab) falls back to the map.
    val currentTab = AppTab.entries.firstOrNull { it.name == selectedTab } ?: AppTab.MAP
    LaunchedEffect(currentTab) {
        if (currentTab != AppTab.MAP) {
            navigation.closeRouteMenu()
            navigation.selectedPlaceId = null
        }
    }
    BackHandler(enabled = currentTab != AppTab.MAP) {
        selectedTab = AppTab.MAP.name
    }
    val mode = resolveThemeMode(settings.themeMode, isSystemInDarkTheme())
    GeorgiaTheme(paletteId = settings.palette, mode = mode) {
        CompositionLocalProvider(LocalLayerAlpha provides settings.layerOpacity) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).aurora()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = { MinimalNavBar(selected = currentTab.name, onSelect = { selectedTab = it }) }
                ) { padding ->
                    AnimatedContent(
                        targetState = currentTab,
                        // Finish fading the previous tab before revealing the next one; avoid blended screens.
                        transitionSpec = { fadeIn(tween(180, delayMillis = 160)) togetherWith fadeOut(tween(160)) },
                        label = "tabs"
                    ) { tab ->
                        when (tab) {
                            AppTab.MAP -> MapScreen(Modifier.padding(padding), settings, navigation, tracker, routeSession, speaker, selectedTrackPoints, trackRecorder.recording, trackRecorder.points.size, { selectedTrackPoints = emptyList() }) { selectedTab = AppTab.TOOLS.name }
                            AppTab.DISCOVER -> DiscoverScreen(Modifier.padding(padding), settings, tracker) { name, latitude, longitude ->
                                routeSession.reset()
                                navigation.startRouteTo(NavigationTarget(name, latitude, longitude))
                                selectedTab = AppTab.MAP.name
                            }
                            AppTab.TOOLS -> ToolsScreen(
                                Modifier.padding(padding),
                                settings,
                                tracker,
                                trackRecorder,
                                trackRepository,
                                recoveryLoaded = recoveryLoaded,
                                onShowTrack = { points ->
                                    selectedTrackPoints = points.map { LatLong(it.latitude, it.longitude) }
                                    routeSession.fitToken++
                                    selectedTab = AppTab.MAP.name
                                },
                                onFollowTrack = { track ->
                                    val current = tracker.location?.takeIf { tracker.hasFreshFix() }
                                    if (current == null) {
                                        tracker.start()
                                        android.widget.Toast.makeText(context, "Сначала определите текущее положение по GPS, затем нажмите «Возврат» ещё раз.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        val reverseRoute = TrackRouteBuilder.reverseFromNearest(
                                            track, current.latitude, current.longitude, routeSession.profile
                                        )
                                        if (reverseRoute == null) {
                                            android.widget.Toast.makeText(context, "Подойдите ближе к линии трека (не дальше 150 м). Если вы уже у начала, возврат не требуется.", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            navigation.clearRoute()
                                            selectedTrackPoints = emptyList()
                                            routeSession.beginTrackFollow(reverseRoute)
                                            routeSession.fitToken++
                                            selectedTab = AppTab.MAP.name
                                        }
                                    }
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(Modifier.padding(padding), settings) { placeId ->
                                navigation.selectedPlaceId = placeId
                                navigation.focusToken++
                                selectedTab = AppTab.MAP.name
                            }
                        }
                    }
                }
                LocationDialogs(tracker)
            }
        }
    }
}

@Composable
internal fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val stagger = remember { Stagger() }
    CompositionLocalProvider(LocalStagger provides stagger) {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Space.screen, end = Space.screen, top = Space.l, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            content = content
        )
    }
}

/** The one surface used for every block of content: lit hairline border, one generous radius, rises in on entry. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, padding: Dp = Space.card, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().appearInOrder(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush())
    ) {
        Column(
            Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = if (padding == 0.dp) Arrangement.Top else Arrangement.spacedBy(Space.s),
            content = content
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = Space.xs, top = Space.s).appearInOrder(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun MinimalNavBar(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tabs = AppTab.entries
    val index = tabs.indexOfFirst { it.name == selected }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.m, vertical = Space.xs)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = scheme.glass(LocalLayerAlpha.current),
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.edgeBrush()),
            shadowElevation = 16.dp
        ) {
            BoxWithConstraints(Modifier.padding(Space.xxs)) {
                val slot = maxWidth / tabs.size
                val slideOffset by animateDpAsState(
                    targetValue = slot * index,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
                    label = "tabOffset"
                )
                // The glowing selection pill slides (with a little overshoot) to the chosen tab.
                Box(
                    Modifier.offset(x = slideOffset).width(slot).height(60.dp)
                        .background(brush = scheme.accentBrush(), shape = CircleShape, alpha = 0.2f)
                )
                Row(Modifier.fillMaxWidth().height(60.dp)) {
                    tabs.forEachIndexed { i, tab ->
                        val active = i == index
                        val tint by animateColorAsState(
                            if (active) scheme.primary else scheme.onSurfaceVariant,
                            animationSpec = tween(240),
                            label = "tabTint"
                        )
                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize().clip(CircleShape).bounceClick { onSelect(tab.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            NavGlyph(kind = i, color = tint)
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Glass pill that floats over the map. */
@Composable
private fun MapChip(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable RowScope.() -> Unit
) = GlassPill(modifier = modifier, onClick = onClick, centered = centered, content = content)

@Composable
private fun MapStylePicker(
    settings: LocalSettings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val choices = LocalSettings.MAP_THEME_CHOICES
    val selectedLabel = choices.firstOrNull { it.first == settings.mapTheme }?.second ?: "Классика"

    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        MapChip(onClick = { onExpandedChange(!expanded) }) {
            Text(
                "Стиль: $selectedLabel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 5 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 5 }
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 208.dp, max = 260.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.glass(LocalLayerAlpha.current),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush()),
                shadowElevation = 6.dp
            ) {
                Column(
                    Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    choices.chunked(2).forEach { rowChoices ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs)
                        ) {
                            rowChoices.forEach { (value, label) ->
                                SelectPill(
                                    label = label,
                                    selected = settings.mapTheme == value,
                                    onClick = {
                                        settings.updateMapTheme(value)
                                        onExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapDetails(
    settings: LocalSettings,
    hasMap: Boolean,
    isImporting: Boolean,
    importMessage: String?,
    importError: Boolean,
    onPickFile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (hasMap) {
            OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth(), enabled = !isImporting) { Text("Заменить файл карты") }
            if (!settings.mapFileIsBundled) {
                OutlinedButton(onClick = { settings.restoreBundledMap() }, modifier = Modifier.fillMaxWidth(), enabled = !isImporting) {
                    Text("Вернуть встроенную карту из APK")
                }
            }
            OutlinedButton(onClick = { settings.setMapFilePath(null) }, modifier = Modifier.fillMaxWidth()) { Text("Отключить карту") }
        } else {
            if (settings.mapDisabled) {
                Button(onClick = { settings.enableBundledMap() }, modifier = Modifier.fillMaxWidth(), enabled = !isImporting) {
                    Text("Включить встроенную карту")
                }
            }
            Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth(), enabled = !isImporting) {
                Text(if (isImporting) "Проверка карты…" else "Выбрать локальный .map-файл")
            }
        }
        if (importMessage != null) {
            Text(
                importMessage,
                color = if (importError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            "Долгое нажатие на карту ставит цель, затем выбери способ передвижения и нажми «Маршрут». Маршруты считаются на телефоне по дорогам OpenStreetMap без интернета; запреты поворотов не учитываются. Положение определяется только GPS телефона и никуда не передаётся.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Карта отрисовывается из локальных файлов Mapsforge. Сетевые источники тайлов не используются. Данные © OpenStreetMap contributors, ODbL 1.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openExternalNavigator(context: android.content.Context, target: NavigationTarget) {
    val label = android.net.Uri.encode(target.name)
    val uri = android.net.Uri.parse("geo:${target.latitude},${target.longitude}?q=${target.latitude},${target.longitude}($label)")
    try {
        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_VIEW, uri), "Открыть в навигаторе"))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Не найдено приложение-навигатор", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun MapScreen(modifier: Modifier, settings: LocalSettings, navigation: NavigationState, tracker: LocationTracker, session: RouteSession, speaker: Speaker, selectedTrackPoints: List<LatLong>, isRecordingTrack: Boolean, recordedTrackPointCount: Int, onClearTrack: () -> Unit, onOpenTrackTools: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // A full-map CI build packages offline/georgia.map in the APK. Keep imported
    // maps untouched; refresh only the bundled copy when its embedded SHA-256 changes.
    LaunchedEffect(settings.mapDisabled, settings.mapFilePath, settings.mapFileIsBundled) {
        val savedPath = settings.mapFilePath
        val savedFileExists = !savedPath.isNullOrBlank() && File(savedPath).isFile
        val keepUserImportedMap = savedFileExists && !settings.mapFileIsBundled
        if (!settings.mapDisabled && !keepUserImportedMap) {
            try {
                val bundled = withContext(Dispatchers.IO) {
                    MapFileRepository.installBundledMap(context)
                }
                if (bundled != null) settings.setMapFilePath(bundled.absolutePath, bundled = true)
            } catch (error: Exception) {
                android.widget.Toast.makeText(
                    context,
                    error.message ?: "Не удалось установить встроенную карту.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val mapFile = settings.mapFilePath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    var poiDatabase by remember { mutableStateOf<File?>(null) }
    var poiDatabaseLoading by remember { mutableStateOf(true) }
    var poiDatabaseMessage by remember { mutableStateOf<String?>(null) }
    var mapSearchError by remember { mutableStateOf<String?>(null) }
    var poiLookupJob by remember { mutableStateOf<Job?>(null) }
    var selectedMapPoi by remember { mutableStateOf<PoiRepository.PoiRecord?>(null) }
    DisposableEffect(Unit) { onDispose { poiLookupJob?.cancel() } }
    var mapSearchQuery by rememberSaveable { mutableStateOf("") }
    var mapSearchCategory by rememberSaveable { mutableStateOf("all") }
    var mapSearchResults by remember { mutableStateOf<List<PoiRepository.PoiRecord>>(emptyList()) }
    var mapSearchViewport by remember { mutableStateOf<MapViewportBounds?>(null) }
    var mapSearchLoading by remember { mutableStateOf(false) }
    var mapStyleExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val installation = PoiRepository.ensureInstalled(context)
        poiDatabase = installation.database
        poiDatabaseMessage = installation.message
        poiDatabaseLoading = false
    }
    LaunchedEffect(poiDatabase, mapSearchViewport, mapSearchQuery, mapSearchCategory) {
        val db = poiDatabase ?: return@LaunchedEffect
        val bounds = mapSearchViewport ?: return@LaunchedEffect
        if (mapSearchQuery.isBlank() && mapSearchCategory == "all") {
            mapSearchResults = emptyList()
            mapSearchError = null
            mapSearchLoading = false
            return@LaunchedEffect
        }
        // Never leave old results tappable while a new query or viewport is being resolved.
        mapSearchResults = emptyList()
        mapSearchError = null
        mapSearchLoading = true
        delay(180)
        try {
            mapSearchResults = PoiRepository.searchInBounds(
                db, mapSearchQuery, bounds.north, bounds.south, bounds.east, bounds.west,
                category = mapSearchCategory, limit = 60
            )
        } catch (error: Exception) {
            mapSearchResults = emptyList()
            mapSearchError = error.message ?: "Не удалось выполнить поиск по локальной базе."
        } finally {
            mapSearchLoading = false
        }
    }
    val preferredLanguages = MapLanguageResolver.orderedLanguages(settings.mapLanguages)
    var recenterToken by remember { mutableIntStateOf(0) }
    var recenterAfterFreshFix by remember { mutableStateOf(false) }
    var recenterFixBaseline by remember { mutableLongStateOf(0L) }
    var zoomInToken by remember { mutableIntStateOf(0) }
    var zoomOutToken by remember { mutableIntStateOf(0) }
    val target = navigation.target
    // Choosing a destination is the person's request for guidance, so start positioning for it.
    // (A route that starts from a chosen point needs no GPS to be drawn.)
    LaunchedEffect(target, navigation.origin) { if (target != null && navigation.origin == null && !tracker.active) tracker.start() }
    LaunchedEffect(tracker.location, tracker.error) {
        val fix = tracker.location
        if (tracker.error != null) {
            recenterAfterFreshFix = false
        } else if (recenterAfterFreshFix && fix != null && tracker.hasFreshFix() && fix.elapsedRealtimeNanos > recenterFixBaseline) {
            recenterToken++
            recenterAfterFreshFix = false
        }
    }

    val savedPlaces = settings.allPlaces()
    val mapPlaces = remember(savedPlaces) {
        savedPlaces.map { MapPlace(it.id, it.latitude, it.longitude, if (it.id == LocalSettings.CAR_PLACE_ID) PlaceKind.CAR else PlaceKind.SAVED) }
    }
    val selectedPlace = savedPlaces.firstOrNull { it.id == navigation.selectedPlaceId }
    BackHandler(
        enabled = navigation.routeMenuVisible || navigation.pointSelection != null ||
            selectedMapPoi != null || navigation.selectedPlaceId != null ||
            (navigation.origin != null && target == null)
    ) {
        when {
            navigation.routeMenuVisible || navigation.pointSelection != null -> navigation.closeRouteMenu()
            selectedMapPoi != null -> selectedMapPoi = null
            navigation.selectedPlaceId != null -> navigation.selectedPlaceId = null
            navigation.origin != null && target == null -> navigation.origin = null
        }
    }
    // Keep contextual cards mutually exclusive with the search panel so they do not stack over the map.
    val showMapSearch = !session.navigating &&
        navigation.pointSelection == null &&
        !navigation.routeMenuVisible &&
        navigation.origin == null &&
        selectedMapPoi == null &&
        selectedPlace == null

    val showMapStylePicker = navigation.pointSelection == null &&
        !navigation.routeMenuVisible &&
        navigation.origin == null &&
        selectedMapPoi == null &&
        selectedPlace == null

    LaunchedEffect(navigation.routeMenuVisible, navigation.pointSelection, navigation.origin, selectedMapPoi?.poiId, selectedPlace?.id) {
        if (navigation.routeMenuVisible || navigation.pointSelection != null ||
            navigation.origin != null || selectedMapPoi != null || selectedPlace != null
        ) {
            mapStyleExpanded = false
        }
    }

    // Route tracking, speech and keep-screen-on are owned by GeorgiaOfflineApp so they
    // continue while another tab is temporarily visible.

    fun planFrom(fix: android.location.Location?, rerouting: Boolean) {
        val goal = navigation.target ?: return
        // "Route from here" starts at the chosen point; otherwise at the GPS position.
        val currentFix = fix?.takeIf { tracker.hasFreshFix() }
        // An off-route recalculation must begin where the person is now. The explicit
        // origin is used only for the initial route; reusing it would send the route
        // backwards to the original starting point.
        val start = navigation.resolveRouteStart(
            currentFix?.latitude,
            currentFix?.longitude,
            rerouting
        ) ?: return
        val waypoints = if (rerouting) session.remainingWaypoints()
            else navigation.viaPoints.map { it.latitude to it.longitude }
        coroutineScope.launch {
            session.plan(
                context,
                start.first,
                start.second,
                goal.latitude,
                goal.longitude,
                rerouting,
                waypoints
            )
            if (!rerouting && session.route != null) {
                delay(7_000)
                if (!session.navigating && session.route != null) session.fitToken++
            }
            if (rerouting && session.navigating && session.voice && session.message == null) speaker.speak("Маршрут перестроен")
        }
    }

    if (mapFile != null) {
        // The map is the interface: it fills the screen and all chrome floats over it.
        Box(
            modifier.fillMaxSize().padding(horizontal = Space.s, vertical = Space.xs).clip(RoundedCornerShape(Radius.card))
        ) {
            MapsforgeMapSurface(
                mapFile = mapFile,
                preferredLanguages = preferredLanguages,
                mapTheme = settings.mapTheme,
                modifier = Modifier.fillMaxSize(),
                userLocation = tracker.location?.takeIf { tracker.hasFreshFix() },
                destination = if (navigation.pointSelection == RoutePointRole.START) null else target?.let { LatLong(it.latitude, it.longitude) },
                accentColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                destinationColor = MaterialTheme.colorScheme.primary.toArgb(),
                recenterToken = recenterToken,
                zoomInToken = zoomInToken,
                zoomOutToken = zoomOutToken,
                routePoints = if (navigation.pointSelection == null) session.route?.points else null,
                trackPoints = selectedTrackPoints,
                viaPoints = session.route?.steps
                    ?.filter { it.maneuver == app.georgia.offline.routing.Maneuver.WAYPOINT }
                    ?.map { LatLong(it.lat, it.lon) }
                    ?: emptyList(),
                routeColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                trackColor = MaterialTheme.colorScheme.secondary.toArgb(),
                followUser = session.navigating,
                fitToken = session.fitToken,
                places = mapPlaces,
                selectedPlaceId = navigation.selectedPlaceId,
                selectedPoi = selectedMapPoi?.let { LatLong(it.latitude, it.longitude) },
                searchResultPoints = if (showMapSearch && !mapStyleExpanded) mapSearchResults.take(6).map { LatLong(it.latitude, it.longitude) } else emptyList(),
                // During turn-by-turn navigation the live GPS marker replaces the
                // historical route-start marker; keeping both is visually misleading.
                origin = navigation.origin?.takeUnless { session.navigating }?.let { LatLong(it.latitude, it.longitude) },
                savedColor = MaterialTheme.colorScheme.primary.toArgb(),
                carColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                originColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                focusToken = navigation.focusToken,
                onPlaceTap = { id ->
                    if (!session.navigating) {
                        poiLookupJob?.cancel()
                        selectedMapPoi = null
                        navigation.selectedPlaceId = id
                    }
                },
                onViewportChanged = { bounds -> mapSearchViewport = bounds },
                onSearchResultTap = { index ->
                    poiLookupJob?.cancel()
                    mapSearchResults.getOrNull(index)?.let { poi ->
                        selectedMapPoi = poi
                        navigation.selectedPlaceId = null
                        mapSearchQuery = ""
                        mapSearchResults = emptyList()
                    }
                },
                onMapTap = { point ->
                    if (!session.navigating) {
                        poiLookupJob?.cancel()
                        navigation.selectedPlaceId = null
                        selectedMapPoi = null
                        poiLookupJob = coroutineScope.launch {
                            val database = poiDatabase ?: return@launch
                            selectedMapPoi = try {
                                PoiRepository.nearestPoi(database, point.latitude, point.longitude)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                },
                onLongPress = { point ->
                    if (!session.navigating) {
                        poiLookupJob?.cancel()
                        session.reset()
                        selectedMapPoi = null
                        navigation.chooseMapPoint("Точка на карте", point.latitude, point.longitude)
                    }
                },
                onRouteDrag = { handle, index, point ->
                    if (!session.navigating) {
                        val moved = NavigationTarget("Точка маршрута", point.latitude, point.longitude)
                        when (handle) {
                            RouteDragTarget.START -> navigation.origin = moved
                            RouteDragTarget.DESTINATION -> navigation.target = moved
                            RouteDragTarget.WAYPOINT -> navigation.updateWaypoint(index, moved)
                            RouteDragTarget.ROUTE_LINE -> {
                                val currentRoute = session.route
                                val along = currentRoute?.project(point.latitude, point.longitude)?.alongMeters ?: Double.MAX_VALUE
                                val insertAt = navigation.viaPoints.indexOfFirst { via ->
                                    currentRoute?.project(via.latitude, via.longitude)?.alongMeters?.let { it > along } == true
                                }.let { if (it < 0) navigation.viaPoints.size else it }
                                navigation.addWaypoint(moved, insertAt)
                            }
                        }
                        session.reset()
                        coroutineScope.launch {
                            delay(120)
                            planFrom(tracker.location, rerouting = false)
                        }
                    }
                }
            )
            if (navigation.pointSelection != null) {
                MapChip(
                    modifier = Modifier.align(Alignment.TopCenter).padding(Space.s),
                    onClick = { navigation.pointSelection = null }
                ) {
                    Text(
                        if (navigation.pointSelection == RoutePointRole.START)
                            "Удерживайте карту: выберите начало маршрута · Отмена"
                        else
                            "Удерживайте карту: выберите пункт назначения · Отмена",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (navigation.pointSelection == null) {
                if (session.navigating && session.route != null) {
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(Space.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        NavigationBanner(session.route!!, session.along, gpsFresh = tracker.hasFreshFix())
                        if (showMapStylePicker) {
                            MapStylePicker(settings, expanded = mapStyleExpanded, onExpandedChange = { mapStyleExpanded = it })
                        }
                        if (selectedTrackPoints.isNotEmpty()) {
                            MapChip(onClick = onClearTrack) { Text("Скрыть GPX-трек") }
                        }
                        if (isRecordingTrack) {
                            MapChip(onClick = onOpenTrackTools) { Text("Запись трека · $recordedTrackPointCount") }
                        }
                    }
                } else {
                    Column(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        val trackerError = tracker.error
                        if (trackerError != null) {
                            MapChip {
                                Text(
                                    trackerError,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (tracker.active && !tracker.hasFreshFix()) {
                            MapChip { Text("Ищу сигнал GPS…", style = MaterialTheme.typography.labelMedium) }
                        }
                        if (showMapStylePicker) {
                            MapStylePicker(settings, expanded = mapStyleExpanded, onExpandedChange = { mapStyleExpanded = it })
                        }
                        if (selectedTrackPoints.isNotEmpty()) {
                            MapChip(onClick = onClearTrack) { Text("Скрыть GPX-трек") }
                        }
                        if (showMapSearch && !mapStyleExpanded) {
                            MapViewportSearchBox(
                                query = mapSearchQuery,
                                onQueryChange = { mapSearchQuery = it },
                                selectedCategory = mapSearchCategory,
                                onCategoryChange = { mapSearchCategory = it },
                                results = mapSearchResults,
                                loading = mapSearchLoading,
                                databaseLoading = poiDatabaseLoading,
                                databaseMessage = poiDatabaseMessage,
                                searchError = mapSearchError,
                                language = settings.language,
                                onSelect = { poi ->
                                    poiLookupJob?.cancel()
                                    selectedMapPoi = poi
                                    navigation.selectedPlaceId = null
                                    mapSearchQuery = ""
                                    mapSearchResults = emptyList()
                                }
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Text(
                    "© OpenStreetMap contributors",
                    modifier = Modifier.align(Alignment.End).padding(end = Space.s),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        MapChip(modifier = Modifier.size(48.dp), onClick = { zoomInToken++ }, centered = true) {
                            ZoomGlyph(color = MaterialTheme.colorScheme.onSurface, zoomIn = true)
                        }
                        MapChip(modifier = Modifier.size(48.dp), onClick = { zoomOutToken++ }, centered = true) {
                            ZoomGlyph(color = MaterialTheme.colorScheme.onSurface, zoomIn = false)
                        }
                    }
                    if (target != null && !navigation.routeMenuVisible && !session.navigating &&
                        navigation.pointSelection == null && selectedMapPoi == null && selectedPlace == null
                    ) {
                        MapChip(onClick = { navigation.openRouteMenu() }) {
                            Text("Маршрут", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Row(
                                Modifier.height(48.dp)
                                    .shadow(12.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.accentBrush())
                                    .bounceClick {
                                        if (!tracker.active || !tracker.hasFreshFix()) {
                                            recenterFixBaseline = tracker.location?.elapsedRealtimeNanos ?: 0L
                                            recenterAfterFreshFix = true
                                            tracker.start()
                                        } else {
                                            recenterToken++
                                        }
                                    }
                                    .padding(horizontal = Space.m),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.xs)
                            ) {
                                CrosshairGlyph(MaterialTheme.colorScheme.onPrimary)
                                Text(
                                    if (tracker.active && tracker.hasFreshFix()) "Где я" else "Найти меня",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                    }
                }
                val activeRoute = session.route
                if ((target != null || session.followingTrack) && session.navigating && activeRoute != null) {
                        NavigationFooter(
                            activeRoute,
                            session,
                            speaker,
                            gpsStatus = if (tracker.hasFreshFix()) null else tracker.error ?: "Жду сигнал GPS…",
                            gpsError = tracker.error != null
                        ) {
                            speaker.stop()
                            session.reset()
                            navigation.clearRoute()
                        }
                    } else if (selectedMapPoi != null) {
                        val poi = selectedMapPoi!!
                        val poiName = poi.nameIn(settings.language).ifBlank { "Без названия" }
                        val alreadySaved = settings.allPlaces().any {
                            kotlin.math.abs(it.latitude - poi.latitude) < 1e-5 &&
                                kotlin.math.abs(it.longitude - poi.longitude) < 1e-5
                        }
                        MapPoiCard(
                            poi = poi,
                            name = poiName,
                            distanceText = tracker.location?.takeIf { tracker.hasFreshFix() }?.let {
                                GeoMath.formatDistance(GeoMath.distanceMeters(it.latitude, it.longitude, poi.latitude, poi.longitude)) + " от вас"
                            },
                            saved = alreadySaved,
                            onRoute = {
                                session.reset()
                                navigation.startRouteTo(NavigationTarget(poiName, poi.latitude, poi.longitude))
                                selectedMapPoi = null
                            },
                            onSave = { settings.addPlace(poiName, poi.latitude, poi.longitude) },
                            onClose = { selectedMapPoi = null }
                        )
                    } else if (selectedPlace != null) {
                        val isCar = selectedPlace.id == LocalSettings.CAR_PLACE_ID
                        SavedPlaceCard(
                            place = selectedPlace,
                            distanceText = tracker.location?.takeIf { tracker.hasFreshFix() }?.let {
                                GeoMath.formatDistance(GeoMath.distanceMeters(it.latitude, it.longitude, selectedPlace.latitude, selectedPlace.longitude)) + " от вас"
                            },
                            isCar = isCar,
                            onRouteHere = {
                                session.reset()
                                navigation.startRouteTo(NavigationTarget(selectedPlace.name, selectedPlace.latitude, selectedPlace.longitude))
                            },
                            onRouteFrom = {
                                session.reset()
                                navigation.startRouteFrom(NavigationTarget(selectedPlace.name, selectedPlace.latitude, selectedPlace.longitude))
                            },
                            onRename = { settings.renamePlace(selectedPlace.id, it) },
                            onDelete = {
                                settings.removePlace(selectedPlace.id)
                                navigation.selectedPlaceId = null
                            },
                            onClose = { navigation.selectedPlaceId = null }
                        )
                    } else if (target != null && navigation.routeMenuVisible && navigation.pointSelection == null) {
                        val alreadySaved = settings.allPlaces().any {
                            kotlin.math.abs(it.latitude - target.latitude) < 1e-5 && kotlin.math.abs(it.longitude - target.longitude) < 1e-5
                        }
                        RoutePlannerCard(
                            target = target,
                            origin = navigation.origin,
                            viaPoints = navigation.viaPoints.toList(),
                            onMoveWaypoint = { from, to ->
                                navigation.moveWaypoint(from, to)
                                session.reset()
                                coroutineScope.launch { delay(120); planFrom(tracker.location, rerouting = false) }
                            },
                            onRemoveWaypoint = { index ->
                                navigation.removeWaypoint(index)
                                session.reset()
                                coroutineScope.launch { delay(120); planFrom(tracker.location, rerouting = false) }
                            },
                            onSave = if (alreadySaved) null else {
                                { settings.addPlace(target.name, target.latitude, target.longitude) }
                            },
                            location = tracker.location?.takeIf { tracker.hasFreshFix() },
                            session = session,
                            onProfile = { option ->
                                session.profile = option
                                if (session.route != null) planFrom(tracker.location, rerouting = session.navigating)
                            },
                            selectionMode = navigation.pointSelection,
                            onChooseOrigin = { session.reset(); navigation.pointSelection = RoutePointRole.START },
                            onChooseDestination = { session.reset(); navigation.pointSelection = RoutePointRole.DESTINATION },
                            onUseMyLocation = {
                                session.reset()
                                navigation.useMyLocation()
                            },
                            onDestinationMyLocation = {
                                tracker.location?.takeIf { tracker.hasFreshFix() }?.let { fix ->
                                    session.reset()
                                    navigation.target = NavigationTarget("Моё местоположение", fix.latitude, fix.longitude)
                                    navigation.routeMenuVisible = true
                                    navigation.pointSelection = null
                                }
                            },
                            onSwap = {
                                session.reset()
                                navigation.swapEndpoints()
                            },
                            onPlan = { planFrom(tracker.location, rerouting = false) },
                            onStart = {
                                session.beginNavigation()
                                mapStyleExpanded = false
                                // Planning controls must leave the map when turn-by-turn guidance starts.
                                // Closing the sheet preserves the route and all of its waypoints.
                                navigation.closeRouteMenu()
                                if (session.voice) activeRoute?.steps?.firstOrNull()?.let { speaker.speak(it.text) }
                            },
                            onExternal = { openExternalNavigator(context, target) },
                            onCancel = { navigation.closeRouteMenu() }
                        )
                    } else if (navigation.origin != null) {
                        OriginHintCard(navigation.origin!!, onCancel = { navigation.origin = null })
                    }
            }
        }
    } else {
        PageColumn(modifier) {
            PageTitle("Карта")
            Panel {
                Text(
                    if (settings.mapDisabled) "Карта отключена" else "Карта недоступна",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (settings.mapDisabled) {
                        "Карта была отключена в настройках. Включите встроенную карту, чтобы продолжить работу."
                    } else {
                        "Не удалось найти встроенную карту в этой сборке. Откройте «Ещё» → «О карте», чтобы выбрать Mapsforge .map-файл Грузии."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings.mapDisabled) {
                    GradientButton(
                        text = "Включить встроенную карту",
                        onClick = settings::enableBundledMap,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MapViewportSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    results: List<PoiRepository.PoiRecord>,
    loading: Boolean,
    databaseLoading: Boolean,
    databaseMessage: String?,
    searchError: String?,
    language: String,
    onSelect: (PoiRepository.PoiRecord) -> Unit
) {
    val categories = listOf(
        "all" to "Все", "food" to "Еда", "transport" to "Транспорт",
        "medical" to "Аптеки и медицина", "tourism" to "Достопримечательности",
        "historic" to "История", "nature" to "Природа", "shops" to "Магазины"
    )
    val resultsScrollState = rememberScrollState()
    LaunchedEffect(query, selectedCategory, results) { resultsScrollState.scrollTo(0) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(50),
                placeholder = { Text("Найти место на этой области карты", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { onQueryChange("") }) { Text("Очистить") }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                categories.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { onCategoryChange(key) },
                        label = { Text(label, maxLines = 1) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            if (databaseLoading) {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Подготавливаю офлайн-базу…",
                        modifier = Modifier.padding(start = Space.s),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (databaseMessage != null) {
                Text(
                    databaseMessage,
                    modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (searchError != null) {
                Text(
                    searchError,
                    modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (query.isNotBlank() || selectedCategory != "all") {
                if (loading) {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Ищу офлайн…",
                            modifier = Modifier.padding(start = Space.s),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        "В видимой области ничего не найдено",
                        modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(resultsScrollState)
                    ) {
                        results.take(6).forEachIndexed { index, poi ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(
                                Modifier.fillMaxWidth().clickable { onSelect(poi) }.padding(horizontal = Space.xs, vertical = Space.xs),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    poi.nameIn(language).ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${PoiLabels.subcategoryLabel(poi.subcategory).ifBlank { poiCategoryLabel(poi.category) }} · ${"%.5f".format(java.util.Locale.ROOT, poi.latitude)}, ${"%.5f".format(java.util.Locale.ROOT, poi.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (results.size > 6) {
                        Text(
                            "Показаны первые 6 из ${results.size}. Уточните запрос.",
                            modifier = Modifier.padding(horizontal = Space.xs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPoiCard(
    poi: PoiRepository.PoiRecord,
    name: String,
    distanceText: String?,
    saved: Boolean,
    onRoute: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    var expanded by remember(poi.poiId) { mutableStateOf(false) }
    val context = LocalContext.current
    val details = poi.detailLines()
    val detailScrollState = rememberScrollState()
    LaunchedEffect(poi.poiId) { detailScrollState.scrollTo(0) }
    val settlement = poi.taggedSettlement()
    Card(
        modifier = Modifier.fillMaxWidth()
            .dismissOnSwipeDown(onClose, canDismiss = { detailScrollState.value == 0 }),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            PoiLabels.subcategoryLabel(poi.subcategory).ifBlank { poiCategoryLabel(poi.category) },
                            settlement,
                            distanceText
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(detailScrollState),
                verticalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                details.take(if (expanded) details.size else 2).forEach { (label, value) ->
                    Text(
                        "$label: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    enabled = details.size > 2
                ) {
                    Text(if (expanded) "Свернуть" else "Подробнее")
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Координаты $name",
                                "${poi.latitude}, ${poi.longitude}"
                            )
                        )
                        android.widget.Toast.makeText(context, "Координаты скопированы", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Координаты")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onRoute, modifier = Modifier.weight(1f)) {
                    Text("Маршрут")
                }
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !saved
                ) {
                    Text(if (saved) "В избранном" else "В избранное", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, settings: LocalSettings, onShowOnMap: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImportingMap by remember { mutableStateOf(false) }
    var mapImportMessage by remember { mutableStateOf<String?>(null) }
    var mapImportError by remember { mutableStateOf(false) }
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isImportingMap = true
                mapImportMessage = "Копирую и проверяю файл карты…"
                mapImportError = false
                try {
                    val file = withContext(Dispatchers.IO) {
                        MapFileRepository.importGeorgiaMap(context, uri)
                    }
                    settings.setMapFilePath(file.absolutePath, bundled = false)
                    mapImportMessage = "Карта проверена: ${file.length() / (1024 * 1024)} МБ"
                } catch (error: Exception) {
                    mapImportError = true
                    mapImportMessage = error.message ?: "Не удалось импортировать файл карты."
                } finally {
                    isImportingMap = false
                }
            }
        }
    }
    val hasMap = settings.mapFilePath?.let(::File)?.let { it.isFile && it.length() > 0L } == true
    val openMapPicker: () -> Unit = { mapPicker.launch(arrayOf("*/*")) }
    PageColumn(modifier) {
        PageTitle("Ещё")

        SectionLabel("Оформление")
        Panel {
            val mode = resolveThemeMode(settings.themeMode, isSystemInDarkTheme())
            Text("Яркость", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                THEME_MODE_CHOICES.forEach { (id, label) ->
                    SelectPill(label = label, selected = settings.themeMode == id, onClick = { settings.updateThemeMode(id) })
                }
            }
            Text(
                "«Сумерки» — мягкая серо-синяя тема между светлой и тёмной. «Авто» следует за настройкой телефона.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Цветовая тема", style = MaterialTheme.typography.titleSmall)
            ThemePicker(settings.palette, mode) { settings.updatePalette(it) }
            Text(
                "Две последние темы контрастные: чёрный и белый с яркими акцентами, для плохого зрения и яркого солнца.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Прозрачность панелей", style = MaterialTheme.typography.titleSmall)
                Text("${(settings.layerOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = settings.layerOpacity,
                onValueChange = { settings.updateLayerOpacity(it) },
                valueRange = LocalSettings.MIN_LAYER_OPACITY..1f
            )
            Text(
                "Меню, карточки, кнопки на карте и нижняя панель: влево прозрачнее, вправо плотнее.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionLabel("Карта")
        Panel {
            Text("Язык названий в списке мест", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("ru" to "Русский", "en" to "English", "ka" to "ქართული").forEach { (code, label) ->
                    SelectPill(label = label, selected = settings.language == code, onClick = { settings.updateLanguage(code) })
                }
            }
            Text(
                "Выбирает язык названий в каталоге мест и карточках. Это не меняет язык подписей на карте.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Языки подписей на карте", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("ru" to "Русский", "en" to "English", "ka" to "ქართული").forEach { (code, label) ->
                    SelectPill(
                        label = label,
                        selected = code in settings.mapLanguages,
                        onClick = { settings.updateMapLanguage(code, code !in settings.mapLanguages) }
                    )
                }
            }
            Text(
                if (settings.mapLanguages.isEmpty()) {
                    "Все переключатели выключены: карта показывает исходные названия из OSM."
                } else {
                    "Можно включить несколько языков одновременно. Доступность каждой подписи зависит от данных OpenStreetMap."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }

        SectionLabel("Мои места")
        Panel {
            SavedPlacesList(settings, onShowOnMap)
        }

        SectionLabel("О карте")
        Panel {
            MapDetails(
                settings = settings,
                hasMap = hasMap,
                isImporting = isImportingMap,
                importMessage = mapImportMessage,
                importError = mapImportError,
                onPickFile = openMapPicker
            )
        }

        Text(
            "Без интернета, аккаунтов, рекламы и аналитики. Координаты никуда не отправляются. Данные карты © OpenStreetMap contributors, ODbL 1.0.",
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePicker(selectedId: String, mode: ThemeMode, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        AppPalettes.forEach { option ->
            val selected = option.id == selectedId
            val preview = option.scheme(mode)
            Column(
                modifier = Modifier.width(76.dp).clip(RoundedCornerShape(Space.m)).clickable { onSelect(option.id) }.padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(preview.background)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            brush = if (selected) MaterialTheme.colorScheme.accentBrush() else SolidColor(MaterialTheme.colorScheme.outline),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(preview.primary, preview.tertiary)))
                    )
                }
                Text(
                    option.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = Space.xs).appearInOrder(), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(brush = MaterialTheme.colorScheme.accentBrush()))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InfoCard(title: String, body: String) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


