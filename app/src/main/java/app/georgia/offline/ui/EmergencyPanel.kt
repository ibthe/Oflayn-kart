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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.georgia.offline.data.EmergencyContact
import app.georgia.offline.data.EmergencyContactRepository
import app.georgia.offline.domain.GeoFormatting
import app.georgia.offline.ui.theme.Space

@Composable
fun EmergencyPanel(context: Context, location: Location?) {
    val coordinates = location?.let { GeoFormatting.decimalCoordinates(it.latitude, it.longitude) }
    val battery = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
    val accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy
    var savedContact by remember { mutableStateOf(EmergencyContactRepository.load(context)) }
    var editingContact by rememberSaveable { mutableStateOf(false) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
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
                "В Грузии 112 — единый номер полиции, скорой и спасателей. Работает круглосуточно; для звонка нужна зона покрытия мобильной сети. Оператор может принять обращение на русском.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            HorizontalDivider()
            Text("Экстренный контакт", style = MaterialTheme.typography.titleSmall)
            if (savedContact != null && !editingContact) {
                Text(
                    "${savedContact!!.name} · ${savedContact!!.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val dialable = savedContact!!.phone.filter { it.isDigit() || it == '+' }
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialable")))
                            }.onFailure {
                                Toast.makeText(context, "Не удалось открыть набор номера.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Позвонить") }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val sms = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${Uri.encode(savedContact!!.phone)}")
                                    putExtra("sms_body", message)
                                }
                                context.startActivity(sms)
                            }.onFailure {
                                Toast.makeText(context, "Не удалось открыть сообщения.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = coordinates != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Отправить координаты") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        contactName = savedContact!!.name
                        contactPhone = savedContact!!.phone
                        editingContact = true
                    }) { Text("Изменить") }
                    TextButton(onClick = {
                        EmergencyContactRepository.clear(context)
                        savedContact = null
                    }) { Text("Удалить") }
                }
            } else {
                if (!editingContact) {
                    Text("Сохрани номер близкого человека. Он останется только на этом устройстве.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { editingContact = true }) { Text("Добавить контакт") }
                } else {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Имя контакта") }
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Телефон с кодом страны") }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        OutlinedButton(
                            onClick = {
                                if (EmergencyContactRepository.save(context, contactName, contactPhone)) {
                                    savedContact = EmergencyContact(contactName.trim(), contactPhone.trim())
                                    editingContact = false
                                    Toast.makeText(context, "Контакт сохранён на устройстве.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Проверь имя и номер телефона.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = EmergencyContactRepository.isValid(contactName, contactPhone),
                            modifier = Modifier.weight(1f)
                        ) { Text("Сохранить") }
                        TextButton(onClick = {
                            editingContact = false
                            contactName = savedContact?.name.orEmpty()
                            contactPhone = savedContact?.phone.orEmpty()
                        }) { Text("Отмена") }
                    }
                }
            }
            Text(
                "Кнопка 112 только открывает набор номера — звонок не совершается автоматически. Координаты не отправляются приложением без твоего действия.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
