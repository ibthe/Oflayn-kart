package app.georgia.offline.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.data.SavedPlace
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.ui.theme.LocalLayerAlpha
import app.georgia.offline.ui.theme.Radius
import app.georgia.offline.ui.theme.Space
import app.georgia.offline.ui.theme.edgeBrush
import app.georgia.offline.ui.theme.glass

/**
 * Shown while a saved point is highlighted on the map: what it is, how far it is, and the two ways to
 * route with it. "Route here" ends at the point; "route from here" starts at it and waits for a destination.
 */
@Composable
fun SavedPlaceCard(
    place: SavedPlace,
    distanceText: String?,
    isCar: Boolean,
    onRouteHere: () -> Unit,
    onRouteFrom: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renaming by remember(place.id) { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().appear(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.glass(LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 16.dp
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(
                        if (isCar) "Метка автомобиля" else "Сохранённое место",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(distanceText, GeoFormatting.decimalCoordinates(place.latitude, place.longitude)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = Space.s)) { Text("Закрыть") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(text = "Маршрут сюда", onClick = onRouteHere, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRouteFrom, modifier = Modifier.weight(1f)) {
                    Text("Маршрут отсюда", maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                if (!isCar) {
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(horizontal = Space.s)) { Text("Переименовать") }
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = Space.s)) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (renaming) {
        var draft by remember(place.id) { mutableStateOf(place.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Название места") },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(80) }, singleLine = true)
            },
            confirmButton = {
                GradientButton(text = "Сохранить", onClick = {
                    onRename(draft)
                    renaming = false
                }, enabled = draft.isNotBlank())
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена") } }
        )
    }
}

/** "Route from here" was chosen; waits for the destination. */
@Composable
fun OriginHintCard(origin: NavigationTarget, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().appear(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.glass(LocalLayerAlpha.current),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.edgeBrush()),
        shadowElevation = 16.dp
    ) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text("Начало маршрута: ${origin.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Теперь выберите, куда ехать: нажмите на другое сохранённое место и «Маршрут сюда», " +
                        "задержите палец на карте или откройте «Места».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    }
}

/** List of everything saved, for the "More" tab. */
@Composable
fun SavedPlacesList(
    settings: LocalSettings,
    onShowOnMap: (String) -> Unit
) {
    val places = settings.allPlaces()
    if (places.isEmpty()) {
        Text(
            "Пока ничего нет. Сохраняйте места из вкладки «Места», долгим нажатием на карте или отметьте автомобиль в «Утилитах».",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        places.forEach { place ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        GeoFormatting.decimalCoordinates(place.latitude, place.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onShowOnMap(place.id) }, contentPadding = PaddingValues(horizontal = Space.s)) { Text("На карте") }
                TextButton(onClick = { settings.removePlace(place.id) }, contentPadding = PaddingValues(horizontal = Space.s)) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
