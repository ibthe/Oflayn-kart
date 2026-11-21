package app.georgia.offline.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import app.georgia.offline.ui.theme.GeorgiaTheme

private enum class AppTab(val label: String, val symbol: String) {
    MAP("Карта", "▦"),
    DISCOVER("Места", "⌖"),
    ROUTE("Маршрут", "➤"),
    SAVED("Моё", "★"),
    TOOLS("Инструменты", "⚒"),
    SETTINGS("Настройки", "⚙")
}

@Composable
fun GeorgiaOfflineApp(settings: LocalSettings) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.MAP.name) }
    GeorgiaTheme(themeMode = settings.appTheme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab.name,
                            onClick = { selectedTab = tab.name },
                            icon = { Text(tab.symbol) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (AppTab.valueOf(selectedTab)) {
                AppTab.MAP -> MapScreen(Modifier.padding(padding), settings)
                AppTab.DISCOVER -> DiscoverScreen(Modifier.padding(padding), settings)
                AppTab.ROUTE -> RouteScreen(Modifier.padding(padding))
                AppTab.SAVED -> SavedScreen(Modifier.padding(padding))
                AppTab.TOOLS -> ToolsScreen(Modifier.padding(padding), settings)
                AppTab.SETTINGS -> SettingsScreen(Modifier.padding(padding), settings)
            }
        }
    }
}

@Composable
private fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun MapScreen(modifier: Modifier, settings: LocalSettings) {
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

    PageColumn(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("GEORGIA OFFLINE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Автономный атлас Грузии", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("OFFLINE", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (mapFile != null) {
            MapsforgeMapSurface(
                mapFile = mapFile,
                preferredLanguage = preferredLanguage,
                mapTheme = settings.mapTheme,
                modifier = Modifier.fillMaxWidth().height(430.dp)
            )
            Text("Подключён локальный файл: ${mapFile.name}", style = MaterialTheme.typography.bodySmall)
            Text("© OpenStreetMap contributors · данные ODbL 1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Язык подписей Mapsforge: ${preferredLanguage ?: "по умолчанию"}. Сейчас движок выбирает один предпочтительный язык; показ нескольких языков одновременно добавим отдельным слоем.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { mapPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting
                ) { Text("Заменить файл карты") }
                if (!settings.mapFileIsBundled) {
                    OutlinedButton(
                        onClick = { settings.restoreBundledMap() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isImporting
                    ) { Text("Вернуть встроенную карту из APK") }
                }
                OutlinedButton(
                    onClick = { settings.setMapFilePath(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отключить карту") }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Векторная карта Mapsforge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🗺️", style = MaterialTheme.typography.displayMedium)
                            Text("Движок карты подключён", fontWeight = FontWeight.SemiBold)
                            Text("В этой сборке нет встроенного файла карты. Полная сборка GitHub Actions создаёт и добавляет его в APK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (settings.mapDisabled) {
                        Button(onClick = { settings.enableBundledMap() }, enabled = !isImporting) {
                            Text("Включить встроенную карту")
                        }
                    }
                    Button(onClick = { mapPicker.launch(arrayOf("*/*")) }, enabled = !isImporting) {
                        Text(if (isImporting) "Проверка карты…" else "Выбрать локальный .map-файл")
                    }
                    Text("Если APK был собран вне GitHub Actions, можно выбрать Mapsforge .map-файл Грузии вручную. Приложение скопирует его во внутреннюю папку и проверит заголовок и пересечение области карты с Грузией.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (importMessage != null) {
            Text(
                importMessage!!,
                color = if (importError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("GPS: по запросу")
            StatusPill("3 языка")
            StatusPill("Без сети")
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Языки названий", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(languageSummary(settings.enabledLanguages), style = MaterialTheme.typography.bodyMedium)
                Text("В сборках GitHub Actions файл карты содержит доступные варианты en / ru / ka. Сейчас отображается один выбранный предпочтительный язык; одновременные многострочные подписи будут добавлены отдельным слоем.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        InfoCard(
            title = "Офлайн-карта",
            body = "Отрисовка работает только с локальными файлами Mapsforge. В полной сборке GitHub Actions карта Грузии создаётся из Geofabrik OpenStreetMap и встраивается прямо в APK; при первом запуске она копируется во внутреннюю папку приложения. Сетевые источники тайлов и загрузка карт во время работы приложения не используются."
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DiscoverScreen(modifier: Modifier, settings: LocalSettings) {
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
        "all" to "Все", "tourism" to "Достопримечательности", "historic" to "История",
        "nature" to "Природа", "food" to "Еда", "lodging" to "Жильё",
        "medical" to "Медицина", "transport" to "Транспорт", "services" to "Сервисы",
        "shops" to "Магазины", "settlement" to "Населённые пункты"
    )

    PageColumn(modifier) {
        PageTitle("Места и POI", "Локальный справочник Грузии · поиск на трёх языках")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск места") },
            placeholder = { Text("Название, адрес, телефон, сайт или ქართულად") },
            singleLine = true
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.chunked(2).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedCategory == id,
                            onClick = { selectedCategory = id },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(
            containerColor = if (statusIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (database == null) {
            InfoCard(
                "Офлайн-справочник пока не встроен",
                "После успешной генерации карты GitHub Actions добавит локальную SQLite-базу POI в APK. Она будет включать только объекты, присутствующие в OpenStreetMap; отсутствующие переводы автоматически не создаются."
            )
        } else if (results.isEmpty() && !loading) {
            InfoCard("Ничего не найдено", "Попробуй другое написание или выбери категорию «Все».")
        } else {
            results.forEach { poi ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val labels = poi.namesFor(settings.enabledLanguages)
                        labels.forEachIndexed { index, label ->
                            Text(
                                label,
                                style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
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
                        Text(
                            "${"%.5f".format(java.util.Locale.US, poi.latitude)}, ${"%.5f".format(java.util.Locale.US, poi.longitude)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedButton(
                            onClick = { copyToClipboard(context, GeoFormatting.decimalCoordinates(poi.latitude, poi.longitude)) }
                        ) { Text("Копировать координаты") }
                    }
                }
            }
        }
        Text(
            "Источник: © OpenStreetMap contributors · ODbL 1.0. Названия показаны на тех языках, которые присутствуют в исходных данных.",
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
private fun RouteScreen(modifier: Modifier) {
    PageColumn(modifier) {
        PageTitle("Планирование маршрута", "Автономные режимы")
        listOf("Автомобиль", "Внедорожник 4×4", "Велосипед", "Пешком / треккинг").forEach { mode ->
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("○", modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode, fontWeight = FontWeight.SemiBold)
                        Text("Профиль маршрутизации будет подключён после графа дорог.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill("В плане")
                }
            }
        }
        InfoCard("Важно для 4×4", "Класс дороги в OSM не гарантирует проходимость. Профиль внедорожника должен учитывать покрытие, уклон, ширину, ограничения и сезонность; неизвестные участки будут помечаться как требующие проверки.")
    }
}

@Composable
private fun SavedScreen(modifier: Modifier) {
    PageColumn(modifier) {
        PageTitle("Мои места и поездки", "Личные данные будут храниться только на устройстве")
        InfoCard("Избранное", "Папки «Хочу посетить», «Посещено» и пользовательские метки появятся вместе с локальной базой POI.")
        InfoCard("Путевой журнал", "Заметки, фотографии, даты поездок и GPX-треки будут сохраняться локально.")
    }
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
        PageTitle("Настройки", "Все параметры сохраняются локально")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Языки названий на карте", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LanguageSwitch("English", "en", settings)
                LanguageSwitch("Русский", "ru", settings)
                LanguageSwitch("ქართული", "ka", settings)
                Text("Когда включено несколько языков, текущая версия карты выбирает один предпочтительный: English → Русский → ქართული. Отдельный режим одновременных многострочных подписей будет добавлен следующим картографическим этапом.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Тема приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceRow(listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная"), settings.appTheme, settings::updateAppTheme)
                HorizontalDivider()
                Text("Стиль карты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    listOf("classic" to "Классика", "outdoor" to "Рельеф", "motorider" to "Автомобильная", "biker" to "Велосипедная"),
                    settings.mapTheme,
                    settings::updateMapTheme
                )
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Хранение карт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (settings.storageTreeUri == null) "Выбрано внутреннее хранилище (по умолчанию)." else "Папка для карт выбрана: SD-карта или другое внешнее хранилище.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { folderPicker.launch(settings.storageUri()) }) {
                    Text("Выбрать папку на SD-карте")
                }
                if (settings.storageTreeUri != null) {
                    Text("Папка выбрана и сохранена. В этой версии карта копируется в защищённую папку приложения; прямое хранение крупных файлов на SD-карте подключим вместе с менеджером данных.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { settings.setStorageTree(null) }) { Text("Сбросить выбор") }
                }
            }
        }
        InfoCard("Конфиденциальность", "В манифесте нет разрешения INTERNET. Нет регистрации, аккаунта, рекламы, аналитики и фоновой отправки координат. GPS будет запрашиваться только при запуске соответствующей функции.")
        InfoCard("Источники карты", "OpenStreetMap contributors · данные ODbL. Условия использования и атрибуция будут показаны в разделе «О приложении».")
    }
}

@Composable
private fun LanguageSwitch(label: String, code: String, settings: LocalSettings) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(label, fontWeight = FontWeight.Medium)
            Text(code.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = code in settings.enabledLanguages, onCheckedChange = { settings.setLanguage(code, it) })
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun languageSummary(languages: Set<String>): String {
    val labels = listOf("en" to "English", "ru" to "Русский", "ka" to "ქართული")
        .filter { it.first in languages }
        .map { it.second }
    return if (labels.isEmpty()) "Ни один язык не включён" else labels.joinToString(" · ")
}
