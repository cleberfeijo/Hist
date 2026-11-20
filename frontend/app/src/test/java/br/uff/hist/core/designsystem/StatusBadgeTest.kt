package br.uff.hist.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.designsystem.components.StatusBadge
import br.uff.hist.core.model.ModerationStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pendingShowsPendente() {
        compose.setContent { HistTheme { StatusBadge(ModerationStatus.PENDING) } }
        compose.onNodeWithText("Pendente").assertIsDisplayed()
    }

    @Test
    fun approvedShowsAprovado() {
        compose.setContent { HistTheme { StatusBadge(ModerationStatus.APPROVED) } }
        compose.onNodeWithText("Aprovado").assertIsDisplayed()
    }

    @Test
    fun rejectedShowsRejeitado() {
        compose.setContent { HistTheme { StatusBadge(ModerationStatus.REJECTED) } }
        compose.onNodeWithText("Rejeitado").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsMessageAndRetryCallsCallback() {
        var retries = 0
        compose.setContent { HistTheme { ErrorState("Falhou", onRetry = { retries++ }) } }
        compose.onNodeWithText("Falhou").assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }
}
