package app.georgia.offline.ui

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.data.MapFileRepository
import app.georgia.offline.data.PoiRepository
import app.georgia.offline.domain.PoiLabels
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.MapLanguageResolver
import app.georgia.offline.domain.FeatureCatalog
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
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.MAP.name) }
    val navigation = remember { NavigationState() }
    val tracker = rememberLocationTracker()
    // A tab name saved by an older version (for example the removed ROUTE tab) falls back to the map.
    val currentTab = AppTab.entries.firstOrNull { it.name == selectedTab } ?: AppTab.MAP
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
                        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
                        label = "tabs"
                    ) { tab ->
                        when (tab) {
                            AppTab.MAP -> MapScreen(Modifier.padding(padding), settings, navigation, tracker)
                            AppTab.DISCOVER -> DiscoverScreen(Modifier.padding(padding), settings, tracker) { name, latitude, longitude ->
                                navigation.target = NavigationTarget(name, latitude, longitude)
                                navigation.selectedPlaceId = null
                                selectedTab = AppTab.MAP.name
                            }
                            AppTab.TOOLS -> ToolsScreen(Modifier.padding(padding), settings, tracker)
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
private fun MapDetails(
    settings: LocalSettings,
    hasMap: Boolean,
    preferredLanguage: String?,
    isImporting: Boolean,
    importMessage: String?,
    importError: Boolean,
    onPickFile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (hasMap) {
            Text("Язык подписей", style = MaterialTheme.typography.titleMedium)
            Text(
                "${preferredLanguage ?: "по умолчанию"} · включено: ${languageSummary(settings.enabledLanguages)}. Карта показывает один язык; одновременные подписи на нескольких языках появятся отдельным слоем.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun MapScreen(modifier: Modifier, settings: LocalSettings, navigation: NavigationState, tracker: LocationTracker) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf(false) }

    // A full-map CI build packages offline/georgia.map in the APK. Keep imported
    // maps untouched; refresh only the bundled copy when its embedded SHA-256 changes.
    LaunchedEffect(settings.mapDisabled, settings.mapFilePath, settings.mapFileIsBundled) {
        val savedPath = settings.mapFilePath
        val savedFileExists = !savedPath.isNullOrBlank() && File(savedPath).isFile
        val keepUserImportedMap = savedFileExists && !settings.mapFileIsBundled
        if (!settings.mapDisabled && !keepUserImportedMap) {
            isImporting = true
            try {
                val bundled = withContext(Dispatchers.IO) {
                    MapFileRepository.installBundledMap(context)
                }
                if (bundled != null) {
                    val wasExistingBundled = settings.mapFileIsBundled && savedPath == bundled.absolutePath
                    settings.setMapFilePath(bundled.absolutePath, bundled = true)
                    if (!wasExistingBundled) {
                        importMessage = "Встроенная карта Грузии установлена (${bundled.length() / (1024 * 1024)} МБ)."
                    } else {
                        importMessage = "Встроенная карта Грузии проверена и актуальна."
                    }
                }
            } catch (error: Exception) {
                importError = true
                importMessage = error.message ?: "Не удалось установить встроенную карту."
            } finally {
                isImporting = false
            }
        }
    }

    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isImporting = true
                importMessage = "Копирую и проверяю файл карты…"
                importError = false
                try {
                    val file = withContext(Dispatchers.IO) {
                        MapFileRepository.importGeorgiaMap(context, uri)
                    }
                    settings.setMapFilePath(file.absolutePath, bundled = false)
                    importMessage = "Карта проверена: ${file.length() / (1024 * 1024)} МБ"
                } catch (error: Exception) {
                    importError = true
                    importMessage = error.message ?: "Не удалось импортировать файл карты."
                } finally {
                    isImporting = false
                }
            }
        }
    }
    val mapFile = settings.mapFilePath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    val preferredLanguage = MapLanguageResolver.preferredLanguage(settings.enabledLanguages)

    val openPicker: () -> Unit = { mapPicker.launch(arrayOf("*/*")) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var recenterToken by remember { mutableIntStateOf(0) }
    var zoomInToken by remember { mutableIntStateOf(0) }
    var zoomOutToken by remember { mutableIntStateOf(0) }
    val target = navigation.target
    // Choosing a destination is the person's request for guidance, so start positioning for it.
    // (A route that starts from a chosen point needs no GPS to be drawn.)
    LaunchedEffect(target, navigation.origin) { if (target != null && navigation.origin == null && !tracker.active) tracker.start() }

    val savedPlaces = settings.allPlaces()
    val mapPlaces = remember(savedPlaces) {
        savedPlaces.map { MapPlace(it.id, it.latitude, it.longitude, if (it.id == LocalSettings.CAR_PLACE_ID) PlaceKind.CAR else PlaceKind.SAVED) }
    }
    val selectedPlace = savedPlaces.firstOrNull { it.id == navigation.selectedPlaceId }

    val session = remember { RouteSession() }
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val hostView = LocalView.current
    DisposableEffect(session.navigating) {
        hostView.keepScreenOn = session.navigating
        onDispose { hostView.keepScreenOn = false }
    }
    // A new or cleared destination invalidates any route.
    LaunchedEffect(target, navigation.origin) { session.reset() }

    fun planFrom(fix: android.location.Location?, rerouting: Boolean) {
        val goal = navigation.target ?: return
        // "Route from here" starts at the chosen point; otherwise at the GPS position.
        val startLat = navigation.origin?.latitude ?: fix?.latitude ?: return
        val startLon = navigation.origin?.longitude ?: fix?.longitude ?: return
        coroutineScope.launch {
            session.plan(context, startLat, startLon, goal.latitude, goal.longitude, rerouting)
            if (rerouting && session.navigating && session.voice && session.message == null) speaker.speak("Маршрут перестроен")
        }
    }

    // Follow GPS fixes while navigating: progress, off-route detection and spoken guidance.
    val fix = tracker.location
    LaunchedEffect(fix, session.navigating) {
        if (!session.navigating || fix == null || navigation.target == null) return@LaunchedEffect
        when (session.update(fix.latitude, fix.longitude, System.currentTimeMillis())) {
            TrackingEvent.ARRIVED -> {
                if (session.voice) speaker.speak("Вы прибыли в пункт назначения")
                android.widget.Toast.makeText(context, "Вы прибыли в пункт назначения", android.widget.Toast.LENGTH_LONG).show()
                navigation.target = null
            }
            TrackingEvent.REROUTE -> planFrom(fix, rerouting = true)
            TrackingEvent.NONE -> session.pendingAnnouncement()?.let { if (session.voice) speaker.speak(it) }
        }
    }

    if (mapFile != null) {
        // The map is the interface: it fills the screen and all chrome floats over it.
        Box(
            modifier.fillMaxSize().padding(horizontal = Space.s, vertical = Space.xs).clip(RoundedCornerShape(Radius.card))
        ) {
            MapsforgeMapSurface(
                mapFile = mapFile,
                preferredLanguage = preferredLanguage,
                mapTheme = settings.mapTheme,
                modifier = Modifier.fillMaxSize(),
                userLocation = tracker.location,
                destination = target?.let { LatLong(it.latitude, it.longitude) },
                accentColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                destinationColor = MaterialTheme.colorScheme.primary.toArgb(),
                recenterToken = recenterToken,
                zoomInToken = zoomInToken,
                zoomOutToken = zoomOutToken,
                routePoints = session.route?.points,
                routeColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                followUser = session.navigating,
                fitToken = session.fitToken,
                places = mapPlaces,
                selectedPlaceId = navigation.selectedPlaceId,
                origin = navigation.origin?.let { LatLong(it.latitude, it.longitude) },
                savedColor = MaterialTheme.colorScheme.primary.toArgb(),
                carColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                originColor = MaterialTheme.colorScheme.tertiary.toArgb(),
                focusToken = navigation.focusToken,
                onPlaceTap = { id -> if (!session.navigating) navigation.selectedPlaceId = id },
                onLongPress = { point ->
                    navigation.chooseMapPoint("Точка на карте", point.latitude, point.longitude)
                }
            )
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(tween(260)) + slideInVertically(tween(360)) { it / 6 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 6 }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = Space.card, end = Space.card, top = Space.card, bottom = Space.xl + Space.l)
                    ) {
                        Text("О карте", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Space.m))
                        MapDetails(settings, true, preferredLanguage, isImporting, importMessage, importError, openPicker)
                    }
                }
            }
            if (showDetails) {
                // The details overlay above is drawn by AnimatedVisibility; no other top chrome while it is open.
            } else if (session.navigating && session.route != null) {
                NavigationBanner(session.route!!, session.along, Modifier.align(Alignment.TopCenter).padding(Space.s))
            } else {
                Column(Modifier.align(Alignment.TopStart).padding(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    MapChip {
                        PulseDot(MaterialTheme.colorScheme.tertiary)
                        Text("Без сети", style = MaterialTheme.typography.labelLarge)
                    }
                    val trackerError = tracker.error
                    if (trackerError != null) {
                        MapChip { Text(trackerError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                    } else if (tracker.active && tracker.location == null) {
                        MapChip { Text("Ищу сигнал GPS…", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Text(
                    "© OpenStreetMap",
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = Space.m, end = Space.m),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    if (!showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            MapChip(modifier = Modifier.size(48.dp), onClick = { zoomInToken++ }, centered = true) {
                                ZoomGlyph(color = MaterialTheme.colorScheme.onSurface, zoomIn = true)
                            }
                            MapChip(modifier = Modifier.size(48.dp), onClick = { zoomOutToken++ }, centered = true) {
                                ZoomGlyph(color = MaterialTheme.colorScheme.onSurface, zoomIn = false)
                            }
                        }
                    } else {
                        Box {}
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        if (!showDetails) {
                            Row(
                                Modifier.height(48.dp)
                                    .shadow(12.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.accentBrush())
                                    .bounceClick {
                                        tracker.start()
                                        recenterToken++
                                    }
                                    .padding(horizontal = Space.m),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.xs)
                            ) {
                                CrosshairGlyph(MaterialTheme.colorScheme.onPrimary)
                                Text(
                                    if (tracker.location != null) "Где я" else "Найти меня",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        if (!session.navigating || showDetails) {
                            MapChip(onClick = { showDetails = !showDetails }) {
                                Text(if (showDetails) "Закрыть" else "О карте", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (!showDetails) {
                    val activeRoute = session.route
                    if (target != null && session.navigating && activeRoute != null) {
                        NavigationFooter(activeRoute, session) {
                            session.reset()
                            navigation.clearRoute()
                        }
                    } else if (selectedPlace != null) {
                        val isCar = selectedPlace.id == LocalSettings.CAR_PLACE_ID
                        SavedPlaceCard(
                            place = selectedPlace,
                            distanceText = tracker.location?.let {
                                GeoMath.formatDistance(GeoMath.distanceMeters(it.latitude, it.longitude, selectedPlace.latitude, selectedPlace.longitude)) + " от вас"
                            },
                            isCar = isCar,
                            onRouteHere = {
                                navigation.target = NavigationTarget(selectedPlace.name, selectedPlace.latitude, selectedPlace.longitude)
                                navigation.pointSelection = null
                                navigation.selectedPlaceId = null
                            },
                            onRouteFrom = {
                                navigation.origin = NavigationTarget(selectedPlace.name, selectedPlace.latitude, selectedPlace.longitude)
                                navigation.pointSelection = null
                                navigation.selectedPlaceId = null
                            },
                            onRename = { settings.renamePlace(selectedPlace.id, it) },
                            onDelete = {
                                settings.removePlace(selectedPlace.id)
                                navigation.selectedPlaceId = null
                            },
                            onClose = { navigation.selectedPlaceId = null }
                        )
                    } else if (target != null) {
                        val alreadySaved = settings.allPlaces().any {
                            kotlin.math.abs(it.latitude - target.latitude) < 1e-5 && kotlin.math.abs(it.longitude - target.longitude) < 1e-5
                        }
                        RoutePlannerCard(
                            target = target,
                            origin = navigation.origin,
                            onSave = if (alreadySaved) null else {
                                { settings.addPlace(target.name, target.latitude, target.longitude) }
                            },
                            location = tracker.location,
                            session = session,
                            onProfile = { option ->
                                session.profile = option
                                if (session.route != null) planFrom(tracker.location, rerouting = true)
                            },
                            selectionMode = navigation.pointSelection,
                            onChooseOrigin = { navigation.pointSelection = RoutePointRole.START },
                            onChooseDestination = { navigation.pointSelection = RoutePointRole.DESTINATION },
                            onUseMyLocation = { navigation.useMyLocation() },
                            onSwap = { navigation.swapEndpoints() },
                            onPlan = { planFrom(tracker.location, rerouting = false) },
                            onStart = {
                                session.beginNavigation()
                                if (session.voice) activeRoute?.steps?.firstOrNull()?.let { speaker.speak(it.text) }
                            },
                            onExternal = { openExternalNavigator(context, target) },
                            onCancel = { navigation.clearRoute() }
                        )
                    } else if (navigation.origin != null) {
                        OriginHintCard(navigation.origin!!, onCancel = { navigation.origin = null })
                    }
                }
            }
        }
    } else {
        PageColumn(modifier) {
            PageTitle("Карта")
            Panel {
                Text("Карта не подключена", style = MaterialTheme.typography.titleMedium)
                Text(
                    "В этой сборке нет встроенного файла карты. Полная сборка GitHub Actions добавляет его в APK; можно также выбрать Mapsforge .map-файл Грузии вручную.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MapDetails(settings, false, preferredLanguage, isImporting, importMessage, importError, openPicker)
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, settings: LocalSettings, onShowOnMap: (String) -> Unit) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settings.setStorageTree(uri)
        }
    }

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
            Text("Язык названий", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("ru" to "Русский", "en" to "English", "ka" to "ქართული").forEach { (code, label) ->
                    SelectPill(label = label, selected = settings.language == code, onClick = { settings.updateLanguage(code) })
                }
            }
            Text(
                "Названия на карте и в списке мест показываются только на этом языке. Если перевода в данных нет, берётся ближайший доступный.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Стиль", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("classic" to "Классика", "outdoor" to "Природа", "motorider" to "Авто", "biker" to "Велосипед").forEach { (value, label) ->
                    SelectPill(label = label, selected = settings.mapTheme == value, onClick = { settings.updateMapTheme(value) })
                }
            }
        }

        SectionLabel("Хранение")
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text("Папка для карт", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (settings.storageTreeUri == null) "Внутреннее хранилище" else "Папка выбрана",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { folderPicker.launch(settings.storageUri()) }) { Text("Выбрать") }
                if (settings.storageTreeUri != null) {
                    TextButton(onClick = { settings.setStorageTree(null) }) { Text("Сбросить") }
                }
            }
            if (settings.storageTreeUri != null) {
                Text(
                    "Пока карта копируется в защищённую папку приложения; прямое хранение на SD-карте появится вместе с менеджером данных.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel("Мои места")
        Panel {
            SavedPlacesList(settings, onShowOnMap)
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

private fun languageSummary(languages: Set<String>): String {
    val labels = listOf("en" to "English", "ru" to "Русский", "ka" to "ქართული")
        .filter { it.first in languages }
        .map { it.second }
    return if (labels.isEmpty()) "Ни один язык не включён" else labels.joinToString(" · ")
}
