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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.georgia.offline.data.LocalSettings
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
                AppTab.DISCOVER -> DiscoverScreen(Modifier.padding(padding))
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Карта Грузии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.fillMaxWidth().height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🗺️", style = MaterialTheme.typography.displayMedium)
                        Text("Картографический пакет ещё не подключён", fontWeight = FontWeight.SemiBold)
                        Text("Векторная карта, рельеф и POI будут добавлены на следующих этапах.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
                Text("Изменить языки можно в настройках. Несколько выбранных языков планируется показывать одновременно.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        InfoCard(
            title = "Проект в разработке",
            body = "Это первый каркас приложения. Реальные карты и маршрутизация пока не встроены; приложение не загружает тайлы и не делает сетевых запросов."
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
private fun DiscoverScreen(modifier: Modifier) {
    PageColumn(modifier) {
        PageTitle("Путеводитель", "Семь модулей будущего автономного атласа")
        FeatureCatalog.groups.forEach { group ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(group.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    group.features.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    StatusPill("Запланировано")
                }
            }
        }
    }
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
                Text("Включённые языки будут показываться одновременно, если для объекта доступны соответствующие названия.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Тема приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceRow(listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная"), settings.appTheme, settings::updateAppTheme)
                HorizontalDivider()
                Text("Стиль карты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    listOf("classic" to "Классика", "outdoor" to "Топография", "night" to "Ночной", "high_contrast" to "Контрастный"),
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
                    Text("Папка сохранена. Реальное размещение файлов будет активировано вместе с менеджером карт.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
