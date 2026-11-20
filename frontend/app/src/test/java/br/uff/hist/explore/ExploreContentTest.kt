package br.uff.hist.explore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.HistTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExploreContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `modo mapa com erro e sheet vazio mostra Tentar de novo no topo`() {
        var retries = 0
        compose.setContent {
            HistTheme {
                ExploreContent(
                    state = ExploreUiState(mode = ExploreMode.MAP, hasPermission = true, sheetTitle = SheetTitle.NEAR_YOU),
                    errorMessage = "Sem conexão. Verifique sua internet.",
                    onSetMode = {}, onCameraIdle = { _, _ -> }, onRetry = { retries++ },
                    onEnableLocation = {}, onOpenLandmark = {}, onSuggestLandmark = {},
                    mapContent = {},
                )
            }
        }
        compose.onNodeWithText("Sem conexão. Verifique sua internet.").assertExists()
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }
}
