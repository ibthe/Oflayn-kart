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
import app.georgia.offline.ui.theme.defaultPaletteId

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
    // A tab name saved by an older version (for example the removed ROUTE tab) falls back to the map.
    val currentTab = AppTab.entries.firstOrNull { it.name == selectedTab } ?: AppTab.MAP
    GeorgiaTheme(palette = settings.palette) {
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
                        AppTab.MAP -> MapScreen(Modifier.padding(padding), settings, navigation)
                        AppTab.DISCOVER -> DiscoverScreen(Modifier.padding(padding), settings) { name, latitude, longitude ->
                            navigation.target = NavigationTarget(name, latitude, longitude)
                            selectedTab = AppTab.MAP.name
                        }
                        AppTab.TOOLS -> ToolsScreen(Modifier.padding(padding), settings)
                        AppTab.SETTINGS -> SettingsScreen(Modifier.padding(padding), settings)
                    }
                }
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
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
            color = scheme.glass(0.94f),
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
private fun MapScreen(modifier: Modifier, settings: LocalSettings, navigation: NavigationState) {
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
    val tracker = rememberLocationTracker()
    var recenterToken by remember { mutableIntStateOf(0) }
    var zoomInToken by remember { mutableIntStateOf(0) }
    var zoomOutToken by remember { mutableIntStateOf(0) }
    val target = navigation.target
    // Choosing a destination is the person's request for guidance, so start positioning for it.
    LaunchedEffect(target) { if (target != null && !tracker.active) tracker.start() }

    val session = remember { RouteSession() }
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val hostView = LocalView.current
    DisposableEffect(session.navigating) {
        hostView.keepScreenOn = session.navigating
        onDispose { hostView.keepScreenOn = false }
    }
    // A new or cleared destination invalidates any route.
    LaunchedEffect(target) { session.reset() }

    fun planFrom(fix: android.location.Location?, rerouting: Boolean) {
        val goal = navigation.target
        if (fix == null || goal == null) return
        coroutineScope.launch {
            session.plan(context, fix.latitude, fix.longitude, goal.latitude, goal.longitude, rerouting)
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
                onLongPress = { point -> navigation.target = NavigationTarget("Точка на карте", point.latitude, point.longitude) }
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
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                            MapChip(modifier = Modifier.size(48.dp), onClick = { zoomOutToken++ }, centered = true) {
                                Text("−", style = MaterialTheme.typography.titleLarge)
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
                if (target != null && !showDetails) {
                    val activeRoute = session.route
                    if (session.navigating && activeRoute != null) {
                        NavigationFooter(activeRoute, session) {
                            session.reset()
                            navigation.target = null
                        }
                    } else {
                        RoutePlannerCard(
                            target = target,
                            location = tracker.location,
                            session = session,
                            onProfile = { option ->
                                session.profile = option
                                if (session.route != null) planFrom(tracker.location, rerouting = true)
                            },
                            onPlan = { planFrom(tracker.location, rerouting = false) },
                            onStart = {
                                session.beginNavigation()
                                if (session.voice) activeRoute?.steps?.firstOrNull()?.let { speaker.speak(it.text) }
                            },
                            onExternal = { openExternalNavigator(context, target) },
                            onCancel = { navigation.target = null }
                        )
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
private fun DiscoverScreen(modifier: Modifier, settings: LocalSettings, onNavigate: (String, Double, Double) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }
    var database by remember { mutableStateOf<File?>(null) }
    var poiCount by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<PoiRepository.PoiRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Подготавливаю локальную базу объектов…") }
    var statusIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val installation = PoiRepository.ensureInstalled(context)
        database = installation.database
        poiCount = installation.poiCount
        statusMessage = installation.message ?: "Локальная база готова: ${installation.poiCount} объектов. Поиск работает без интернета."
        statusIsError = installation.message != null
        loading = false
    }

    LaunchedEffect(database, query, selectedCategory) {
        val localDatabase = database ?: return@LaunchedEffect
        delay(180)
        loading = true
        try {
            results = PoiRepository.search(localDatabase, query, selectedCategory)
            statusIsError = false
            statusMessage = "Найдено: ${results.size}. В базе: $poiCount объектов."
        } catch (error: Exception) {
            results = emptyList()
            statusIsError = true
            statusMessage = error.message ?: "Не удалось выполнить локальный поиск."
        } finally {
            loading = false
        }
    }

    val categories = listOf(
        "all" to "Все", "tourism" to "Туризм", "historic" to "История",
        "nature" to "Природа", "food" to "Еда", "lodging" to "Жильё",
        "medical" to "Медицина", "transport" to "Транспорт", "services" to "Сервисы",
        "shops" to "Магазины", "settlement" to "Города и села"
    )

    PageColumn(modifier) {
        PageTitle("Места")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            placeholder = { Text("Название, адрес, телефон, ქართულად") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            categories.forEach { (id, label) ->
                SelectPill(label = label, selected = selectedCategory == id, onClick = { selectedCategory = id })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (database == null) {
            InfoCard(
                "Офлайн-справочник пока не встроен",
                "После успешной генерации карты GitHub Actions добавит локальную SQLite-базу POI в APK. Она включает только объекты из OpenStreetMap; отсутствующие переводы автоматически не создаются."
            )
        } else if (results.isEmpty() && !loading) {
            InfoCard("Ничего не найдено", "Попробуй другое написание или выбери категорию «Все».")
        } else {
            Panel(padding = 0.dp) {
                results.forEachIndexed { index, poi ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Space.card), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Space.card, vertical = Space.m),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs)
                    ) {
                        poi.namesFor(settings.enabledLanguages).forEachIndexed { nameIndex, label ->
                            if (nameIndex == 0) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                            } else {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "${poiCategoryLabel(poi.category)} · ${PoiLabels.subcategoryLabel(poi.subcategory)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        poi.detailLines().forEach { (label, value) ->
                            Text(
                                "$label: $value",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${"%.5f".format(java.util.Locale.US, poi.latitude)}, ${"%.5f".format(java.util.Locale.US, poi.longitude)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            TextButton(onClick = {
                                onNavigate(poi.namesFor(settings.enabledLanguages).firstOrNull() ?: "Цель", poi.latitude, poi.longitude)
                            }) { Text("Навигация") }
                            TextButton(onClick = { copyToClipboard(context, GeoFormatting.decimalCoordinates(poi.latitude, poi.longitude)) }) {
                                Text("Копировать")
                            }
                        }
                    }
                }
            }
        }
        Text(
            "© OpenStreetMap contributors · ODbL 1.0. Названия показаны на тех языках, которые есть в исходных данных.",
            modifier = Modifier.padding(horizontal = Space.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun poiCategoryLabel(category: String): String = when (category) {
    "food" -> "Еда и напитки"
    "lodging" -> "Жильё"
    "tourism" -> "Достопримечательности"
    "medical" -> "Медицина"
    "transport" -> "Транспорт и заправки"
    "services" -> "Сервисы"
    "shops" -> "Магазины"
    "historic" -> "История и культура"
    "nature" -> "Природа"
    "settlement" -> "Населённые пункты"
    else -> category
}

@Composable
private fun SettingsScreen(modifier: Modifier, settings: LocalSettings) {
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
            ThemePicker(settings.palette ?: defaultPaletteId(isSystemInDarkTheme())) { settings.updatePalette(it) }
        }

        SectionLabel("Карта")
        Panel {
            Text("Язык названий", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("en" to "English", "ru" to "Русский", "ka" to "ქართული").forEach { (code, label) ->
                    SelectPill(label = label, selected = code in settings.enabledLanguages, onClick = { settings.setLanguage(code, code !in settings.enabledLanguages) })
                }
            }
            Text(
                "На карте показывается один язык: English → Русский → ქართული.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Стиль", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf("classic" to "Классика", "outdoor" to "Рельеф", "motorider" to "Авто", "biker" to "Велосипед").forEach { (value, label) ->
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

        SectionLabel("Скоро")
        Panel(padding = 0.dp) {
            listOf(
                "Мои места" to "Избранное, заметки и GPX-треки; все данные только на устройстве."
            ).forEachIndexed { index, (title, body) ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Space.card), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.card, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
private fun ThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppPalettes.forEach { option ->
            val selected = option.id == selectedId
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(Space.m)).clickable { onSelect(option.id) }.padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(option.scheme.background)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            brush = if (selected) MaterialTheme.colorScheme.accentBrush() else SolidColor(MaterialTheme.colorScheme.outline),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(option.scheme.primary, option.scheme.tertiary)))
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
