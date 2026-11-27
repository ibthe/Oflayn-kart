package app.georgia.offline.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.ui.theme.Space

@Composable
fun EmergencyPanel(context: Context, location: Location?) {
    val coordinates = location?.let { GeoFormatting.decimalCoordinates(it.latitude, it.longitude) }
    val battery = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
    val accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy
    val message = buildString {
        append("Нужна помощь. Мои координаты: ")
        append(coordinates ?: "актуальная GPS-позиция недоступна")
        accuracyMeters?.let { append(". Точность GPS около ${it.toInt()} м") }
        battery?.let { append(". Заряд телефона: $it%") }
    }

    Panel(padding = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Экстренная помощь", style = MaterialTheme.typography.titleMedium)
                Text("112", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                coordinates?.let { "Текущие координаты: $it" } ?: "Нет актуальной GPS-позиции. Включи геолокацию и дождись фикса.",
                style = MaterialTheme.typography.bodySmall,
                color = if (coordinates == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOfNotNull(
                    accuracyMeters?.let { "Точность ±${it.toInt()} м" },
                    battery?.let { "Заряд $it%" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                        }.onFailure {
                            Toast.makeText(context, "Не удалось открыть набор номера.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Набрать 112", maxLines = 1) }
                OutlinedButton(
                    onClick = {
                        if (coordinates != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Координаты", message))
                            Toast.makeText(context, "Координаты скопированы.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = coordinates != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Копировать", maxLines = 1) }
            }
            TextButton(
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(intent, "Передать координаты"))
                    }.onFailure {
                        Toast.makeText(context, "Нет приложения для передачи сообщения.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = coordinates != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Поделиться координатами") }
            Text(
                "Кнопка 112 только открывает набор номера — звонок не совершается автоматически. Координаты не отправляются приложением без твоего действия.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
