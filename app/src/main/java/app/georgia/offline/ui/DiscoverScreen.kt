package app.georgia.offline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.data.PoiRepository
import app.georgia.offline.data.TripPlanRepository
import app.georgia.offline.data.TripPlanStop
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.domain.GeoMath
import app.georgia.offline.domain.PoiLabels
import app.georgia.offline.ui.theme.Space
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The places directory: one compact row per object in the chosen language, with the settlement it lies in.
 * Tapping a row opens the full information in place; tapping again folds it back.
 */
@Composable
internal fun DiscoverScreen(
    modifier: Modifier,
    settings: LocalSettings,
    tracker: LocationTracker,
    tripPlanRepository: TripPlanRepository,
    tripPlanRevision: Int,
    onNavigate: (String, Double, Double) -> Unit,
    onPlanRoute: (List<PoiRepository.PoiRecord>) -> Unit
) {
    val context = LocalContext.current
    val language = settings.language
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var database by remember { mutableStateOf<File?>(null) }
    var poiCount by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<PoiRepository.PoiRecord>>(emptyList()) }
    var settlementOf by remember { mutableStateOf<Map<String, PoiRepository.Settlement?>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Подготавливаю локальную базу объектов…") }
    var statusIsError by remember { mutableStateOf(false) }
    var planMode by rememberSaveable { mutableStateOf(false) }
    var tripStops by remember { mutableStateOf(tripPlanRepository.list()) }
    var tripPois by remember { mutableStateOf<List<PoiRepository.PoiRecord>>(emptyList()) }
    val here = tracker.location?.takeIf { tracker.hasFreshFix() }

    LaunchedEffect(Unit) {
        loading = true
        val installation = PoiRepository.ensureInstalled(context)
        database = installation.database
        poiCount = installation.poiCount
        statusMessage = installation.message ?: "В базе $poiCount объектов. Поиск работает без интернета."
        statusIsError = installation.message != null
        loading = false
    }

    LaunchedEffect(tripPlanRevision, database) {
        val stops = tripPlanRepository.list()
        tripStops = stops
        val localDatabase = database
        tripPois = if (localDatabase != null) {
            PoiRepository.byIds(localDatabase, stops.map { it.poiId })
        } else {
            emptyList()
        }
    }

    LaunchedEffect(database, query, selectedCategory) {
        val localDatabase = database ?: return@LaunchedEffect
        // Do not leave old rows tappable while a new offline query is pending.
        results = emptyList()
        settlementOf = emptyMap()
        expandedId = null
        loading = true
        statusIsError = false
        statusMessage = "Ищу в офлайн-базе…"
        delay(180)
        try {
            val found = PoiRepository.search(localDatabase, query, selectedCategory)
            val settlements = PoiRepository.settlements(localDatabase)
            settlementOf = withContext(Dispatchers.Default) {
                found.associate { it.poiId to PoiRepository.nearestSettlement(settlements, it.latitude, it.longitude) }
            }
            results = found
            statusIsError = false
            statusMessage = "Найдено: ${found.size}"
        } catch (error: Exception) {
            results = emptyList()
            statusIsError = true
            statusMessage = error.message ?: "Не удалось выполнить локальный поиск."
        } finally {
            loading = false
        }
    }

    // With no search text and a known position, the nearest objects come first.
    val ordered = remember(results, here, query) {
        if (here != null && query.isBlank()) {
            results.sortedBy { GeoMath.distanceMeters(here.latitude, here.longitude, it.latitude, it.longitude) }
        } else {
            results
        }
    }

    PageColumn(modifier) {
        PageTitle("Места")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            SelectPill("Каталог", selected = !planMode, onClick = { planMode = false })
            SelectPill("План поездки · ${tripStops.size}", selected = planMode, onClick = { planMode = true })
        }
        if (planMode) {
            TripPlanContent(
                stops = tripStops,
                pois = tripPois,
                language = language,
                onPlanRoute = onPlanRoute,
                onMove = { id, direction ->
                    tripPlanRepository.move(id, direction)
                    tripStops = tripPlanRepository.list()
                },
                onSetDay = { id, day ->
                    tripPlanRepository.setDay(id, day)
                    tripStops = tripPlanRepository.list()
                },
                onRemove = { id ->
                    tripPlanRepository.remove(id)
                    tripStops = tripPlanRepository.list()
                }
            )
        } else {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            placeholder = { Text("Название, адрес, телефон", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("Сбросить") }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            PLACE_CATEGORIES.forEach { (id, label) ->
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
                "После успешной генерации карты GitHub Actions добавит локальную базу объектов в приложение. Она включает только данные OpenStreetMap."
            )
        } else if (ordered.isEmpty() && !loading) {
            InfoCard("Ничего не найдено", "Попробуйте другое написание или категорию «Все».")
        } else {
            Panel(padding = 0.dp) {
                ordered.forEachIndexed { index, poi ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Space.m), color = MaterialTheme.colorScheme.outlineVariant)
                    val settlement = settlementOf[poi.poiId]
                    val settlementName = if (poi.category == "settlement") {
                        null
                    } else {
                        settlement?.nameIn(language)?.takeIf { it.isNotBlank() } ?: poi.taggedSettlement()
                    }
                    val distance = here?.let { GeoMath.formatDistance(GeoMath.distanceMeters(it.latitude, it.longitude, poi.latitude, poi.longitude)) }
                    val name = poi.nameIn(language).ifBlank { "Без названия" }
                    val alreadySaved = settings.savedPlaces.any { abs(it.latitude - poi.latitude) < 1e-5 && abs(it.longitude - poi.longitude) < 1e-5 }
                    PlaceRow(
                        poi = poi,
                        name = name,
                        settlementName = settlementName,
                        distance = distance,
                        expanded = expandedId == poi.poiId,
                        saved = alreadySaved,
                        onToggle = { expandedId = if (expandedId == poi.poiId) null else poi.poiId },
                        onRoute = { onNavigate(name, poi.latitude, poi.longitude) },
                        onSave = { settings.addPlace(name, poi.latitude, poi.longitude) },
                        inTrip = tripStops.any { it.poiId == poi.poiId },
                        onAddToTrip = {
                            if (tripPlanRepository.add(poi.poiId)) {
                                tripStops = tripPlanRepository.list()
                                tripPois = (tripPois + poi).distinctBy { it.poiId }
                                statusMessage = "Добавлено в план поездки."
                            } else {
                                statusMessage = "Это место уже есть в плане."
                            }
                        },
                        onCopy = { copyToClipboard(context, GeoFormatting.decimalCoordinates(poi.latitude, poi.longitude)) }
                    )
                }
            }
        }
        Text(
            "© OpenStreetMap contributors · ODbL 1.0. Названия показаны на выбранном языке; если перевода в данных нет, берётся ближайший доступный.",
            modifier = Modifier.padding(horizontal = Space.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

@Composable
private fun TripPlanContent(
    stops: List<TripPlanStop>,
    pois: List<PoiRepository.PoiRecord>,
    language: String,
    onPlanRoute: (List<PoiRepository.PoiRecord>) -> Unit,
    onMove: (String, Int) -> Unit,
    onSetDay: (String, Int) -> Unit,
    onRemove: (String) -> Unit
) {
    val byId = remember(pois) { pois.associateBy { it.poiId } }
    if (stops.isEmpty()) {
        InfoCard(
            "План пока пуст",
            "Добавляйте интересные места из каталога. План хранится на устройстве и доступен без интернета."
        )
        return
    }
    stops.groupBy { it.day }.toSortedMap().forEach { (day, dayStops) ->
        Panel(padding = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("День $day", style = MaterialTheme.typography.titleMedium)
                        Text("${dayStops.size} мест", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { onPlanRoute(dayStops.mapNotNull { byId[it.poiId] }) },
                        enabled = dayStops.any { byId[it.poiId] != null }
                    ) { Text("Маршрут дня") }
                }
                dayStops.forEachIndexed { index, stop ->
                    val poi = byId[stop.poiId]
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            poi?.nameIn(language)?.ifBlank { "Без названия" } ?: "Место отсутствует в офлайн-базе",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        poi?.let {
                            Text(
                                "${poiCategoryLabel(it.category)} · ${"%.5f".format(java.util.Locale.US, it.latitude)}, ${"%.5f".format(java.util.Locale.US, it.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        stop.note.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            TextButton(onClick = { onMove(stop.poiId, -1) }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { onMove(stop.poiId, 1) }, enabled = index < dayStops.lastIndex) { Text("↓") }
                            TextButton(onClick = { onSetDay(stop.poiId, day + 1) }, enabled = day < 30) { Text("День +") }
                            TextButton(onClick = { onRemove(stop.poiId) }) { Text("Убрать") }
                        }
                    }
                }
            }
        }
    }
}

private val PLACE_CATEGORIES = listOf(
    "all" to "Все", "tourism" to "Туризм", "historic" to "История",
    "nature" to "Природа", "food" to "Еда", "lodging" to "Жильё",
    "medical" to "Медицина", "transport" to "Транспорт", "services" to "Сервисы",
    "shops" to "Магазины", "settlement" to "Города и села"
)

@Composable
private fun PlaceRow(
    poi: PoiRepository.PoiRecord,
    name: String,
    settlementName: String?,
    distance: String?,
    expanded: Boolean,
    saved: Boolean,
    inTrip: Boolean,
    onToggle: () -> Unit,
    onRoute: () -> Unit,
    onSave: () -> Unit,
    onAddToTrip: () -> Unit,
    onCopy: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val kind = PoiLabels.subcategoryLabel(poi.subcategory).ifBlank { poiCategoryLabel(poi.category) }
    val subtitle = listOfNotNull(kind, settlementName, distance).joinToString(" · ")
    Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(260))) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).bounceClick(onClick = onToggle)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(categoryTint(poi.category).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    poiCategoryLabel(poi.category).take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = categoryTint(poi.category),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (expanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Chevron(expanded = expanded, color = scheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = Space.m + 44.dp + Space.s, end = Space.m, bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                InfoLine("Категория", poiCategoryLabel(poi.category))
                if (settlementName != null) InfoLine("Населённый пункт", settlementName)
                poi.detailLines().forEach { (label, value) -> InfoLine(label, value) }
                InfoLine("Координаты", "${"%.5f".format(java.util.Locale.US, poi.latitude)}, ${"%.5f".format(java.util.Locale.US, poi.longitude)}")
                // Keep the two primary actions side by side, with equal visual weight.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientButton(
                        text = "Маршрут",
                        onClick = onRoute,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !saved
                    ) {
                        Text(
                            if (saved) "В избранном" else "В избранное",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onAddToTrip, enabled = !inTrip) {
                        Text(if (inTrip) "В плане" else "Добавить в план")
                    }
                    TextButton(onClick = onCopy) { Text("Копировать координаты") }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Chevron(expanded: Boolean, color: Color) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "chevron")
    Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = angle }) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.5f, h * 0.64f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.78f, h * 0.38f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun categoryTint(category: String): Color = when (category) {
    "food", "shops" -> MaterialTheme.colorScheme.tertiary
    "nature", "lodging" -> MaterialTheme.colorScheme.primary
    "medical" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

internal fun poiCategoryLabel(category: String): String = when (category) {
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
