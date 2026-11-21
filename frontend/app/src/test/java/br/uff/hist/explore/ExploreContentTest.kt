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
                    onEnableLocation = {}, onEnablePrecise = {}, onCenteredOnUser = {},
                    onOpenLandmark = {}, onSuggestLandmark = {},
                    mapContent = {},
                )
            }
        }
        compose.onNodeWithText("Sem conexão. Verifique sua internet.").assertExists()
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }

    private fun show(state: ExploreUiState, onEnablePrecise: () -> Unit = {}) {
        compose.setContent {
            HistTheme {
                ExploreContent(
                    state = state, errorMessage = null,
                    onSetMode = {}, onCameraIdle = { _, _ -> }, onRetry = {},
                    onEnableLocation = {}, onEnablePrecise = onEnablePrecise, onCenteredOnUser = {},
                    onOpenLandmark = {}, onSuggestLandmark = {},
                    mapContent = {},
                )
            }
        }
    }

    private val listMode = ExploreUiState(mode = ExploreMode.LIST, hasPermission = true, hasPreciseLocation = true)

    @Test fun `lista vazia depois de carregar mostra o estado vazio de perto de voce`() {
        show(listMode.copy(sheetTitle = SheetTitle.NEAR_YOU, sheetLoaded = true))
        compose.onNodeWithText("Nenhum ponto histórico por perto.").assertExists()
    }

    @Test fun `lista vazia da area mostra o estado vazio de nesta area`() {
        show(listMode.copy(hasPermission = false, sheetTitle = SheetTitle.THIS_AREA, sheetLoaded = true))
        compose.onNodeWithText("Nenhum ponto histórico nesta área.").assertExists()
    }

    @Test fun `antes da primeira busca mostra carregando e nao o estado vazio`() {
        show(listMode.copy(sheetTitle = SheetTitle.NEAR_YOU, sheetLoaded = false))
        compose.onNodeWithText("Nenhum ponto histórico por perto.").assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertExists()
    }

    @Test fun `so localizacao aproximada mostra o aviso com a acao de ativar a precisa`() {
        var asked = 0
        show(listMode.copy(hasPreciseLocation = false), onEnablePrecise = { asked++ })
        compose.onNodeWithText("Sua localização está aproximada. Ative a precisa para usar o chat.").assertExists()
        compose.onNodeWithText("Ativar precisa").performClick()
        assertEquals(1, asked)
    }

    @Test fun `com localizacao precisa nao ha aviso de aproximada`() {
        show(listMode)
        compose.onNodeWithText("Sua localização está aproximada. Ative a precisa para usar o chat.").assertDoesNotExist()
    }
}
