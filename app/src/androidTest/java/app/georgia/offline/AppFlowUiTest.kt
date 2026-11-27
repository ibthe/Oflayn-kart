package app.georgia.offline

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun stylePickerAndTabTransitionsKeepPanelsExclusive() {
        awaitText("Карта")
        composeRule.onNodeWithText("Карта", substring = false).performClick()
        composeRule.onAllNodesWithText("Найти место на этой области карты").assertCountEquals(1)

        // Expanding the style picker must not leave the search panel stacked beneath it.
        composeRule.onAllNodesWithText("Стиль:", substring = true).onFirst().performClick()
        awaitText("Спутниковый слой")
        awaitText("Импортировать ZIP")
        composeRule.onAllNodesWithText("Найти место на этой области карты").assertCountEquals(0)
        composeRule.onNodeWithText("Oflayn Natural", substring = false).performClick()
        awaitText("Стиль: Oflayn Natural")
        composeRule.onAllNodesWithText("Найти место на этой области карты").assertCountEquals(1)

        // At the end of each transition, only the target page title remains beside its tab label.
        composeRule.onNodeWithText("Места", substring = false).performClick()
        awaitCount("Места", 2)
        composeRule.onNodeWithText("План поездки · 0", substring = false).performClick()
        composeRule.onAllNodesWithText("План пока пуст", substring = false).assertCountEquals(1)
        composeRule.onNodeWithText("Каталог", substring = false).performClick()
        composeRule.onNodeWithText("Путеводитель", substring = false).performClick()
        composeRule.onAllNodesWithText("Тушетия", substring = false).assertCountEquals(1)
        composeRule.onNodeWithText("Каталог", substring = false).performClick()
        composeRule.onNodeWithText("Утилиты", substring = false).performClick()
        awaitCount("Утилиты", 2)
        composeRule.onAllNodesWithText("Экстренная помощь", substring = false).assertCountEquals(1)
        awaitText("Состояние устройства")
        composeRule.onAllNodesWithText("Заряд батареи", substring = false).assertCountEquals(1)
        composeRule.onAllNodesWithText("Сеть", substring = false).assertCountEquals(1)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Места", substring = false).assertCountEquals(1)
        composeRule.onAllNodesWithText("Запись и GPX-треки", substring = false).assertCountEquals(1)
        composeRule.onAllNodesWithText("Импорт GPX", substring = false).assertCountEquals(1)
        composeRule.onAllNodesWithText("Заметка к треку", substring = false).assertCountEquals(1)

        composeRule.onNodeWithText("Ещё", substring = false).performClick()
        awaitCount("Ещё", 2)
        awaitText("Хранение")
        awaitText("Внутренняя память")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Утилиты", substring = false).assertCountEquals(1)

        composeRule.onNodeWithText("Карта", substring = false).performClick()
        awaitText("Стиль: Oflayn Natural")
        composeRule.onNodeWithText("Найти место на этой области карты").assertExists()
    }

    @Test
    fun guidebookAndEmergencyToolsExposeTheirExpandedContent() {
        awaitText("Карта")
        composeRule.onNodeWithText("Карта", substring = false).performClick()
        composeRule.onNodeWithText("Места", substring = false).performClick()
        awaitCount("Места", 2)
        composeRule.onNodeWithText("Путеводитель", substring = false).performClick()
        awaitText("Тушетия")
        composeRule.onAllNodesWithText("Подробнее", substring = false).onFirst().performClick()
        awaitText("Маршруты и последовательности")
        awaitText("Полезные сервисы и подготовка")
        awaitCount("Найти", 4)

        composeRule.onNodeWithText("Карта", substring = false).performClick()
        composeRule.onNodeWithText("Утилиты", substring = false).performClick()
        awaitText("Экстренная помощь")
        awaitText("Если произошло ДТП")
        composeRule.onNodeWithText("Открыть", substring = false).performClick()
        awaitText("Сначала безопасность")
        awaitText("Вызови 112")
        composeRule.onNodeWithText("Добавить контакт", substring = false).performClick()
        awaitText("Имя контакта")
        awaitText("Телефон с кодом страны")
        composeRule.onNodeWithText("Отмена", substring = false).performClick()
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitCount(text: String, count: Int) {
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size >= count
        }
    }
}
