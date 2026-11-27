package app.georgia.offline

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.georgia.offline.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun stylePickerAndTabTransitionsKeepPanelsExclusive() {
        awaitText("Стиль: Классика")
        composeRule.onNodeWithText("Найти место на этой области карты").assertExists()

        // Expanding the style picker must not leave the search panel stacked beneath it.
        composeRule.onNodeWithText("Стиль: Классика").performClick()
        composeRule.onNodeWithText("Найти место на этой области карты").assertDoesNotExist()
        composeRule.onNodeWithText("Oflayn Natural", substring = false).performClick()
        awaitText("Стиль: Oflayn Natural")
        composeRule.onNodeWithText("Найти место на этой области карты").assertExists()

        // At the end of each transition, only the target page title remains beside its tab label.
        composeRule.onNodeWithText("Места", substring = false).performClick()
        awaitCount("Места", 2)
        composeRule.onNodeWithText("Утилиты", substring = false).performClick()
        awaitCount("Утилиты", 2)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Места", substring = false).assertCountEquals(1)

        composeRule.onNodeWithText("Ещё", substring = false).performClick()
        awaitCount("Ещё", 2)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Утилиты", substring = false).assertCountEquals(1)

        composeRule.onNodeWithText("Карта", substring = false).performClick()
        awaitText("Стиль: Oflayn Natural")
        composeRule.onNodeWithText("Найти место на этой области карты").assertExists()
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
