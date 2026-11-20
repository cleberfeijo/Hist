package br.uff.hist.explore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.model.Category
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.ModerationStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun landmark(id: String, name: String) = Landmark(
    id = id, name = name, description = null, location = GeoPoint(0.0, 0.0), category = Category.IGREJA,
    city = null, state = null, imageUrl = null, source = null, status = ModerationStatus.APPROVED,
    rejectionReason = null, curiosityCount = 3, createdBy = null,
)

@RunWith(RobolectricTestRunner::class)
class LandmarkListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `item com chat mostra o chip Chat e item distante mostra a distancia`() {
        var clicked: String? = null
        compose.setContent {
            HistTheme {
                LandmarkList(
                    items = listOf(
                        LandmarkItem(landmark("a", "Igreja Matriz"), 87.0, true),
                        LandmarkItem(landmark("b", "Marco"), 410.0, false),
                    ),
                    onClick = { clicked = it },
                )
            }
        }
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onNodeWithText("410 m").assertIsDisplayed()
        compose.onNodeWithText("Marco").performClick()
        assertEquals("b", clicked)
    }

    @Test fun `sem distancia nao mostra metros`() {
        compose.setContent {
            HistTheme { LandmarkList(listOf(LandmarkItem(landmark("a", "Igreja"), null, false)), onClick = {}) }
        }
        compose.onNodeWithText("Igreja").assertIsDisplayed()
        compose.onNodeWithText("Chat").assertDoesNotExist()
    }
}
