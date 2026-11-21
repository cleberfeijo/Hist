package br.uff.hist.landmark

import android.content.Context
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.uff.hist.R
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.model.Category
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.ModerationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val LANDMARK = Landmark(
    id = "p1", name = "Igreja Matriz", description = "Fundada em 1750.", location = GeoPoint(0.0, 0.0),
    category = Category.IGREJA, city = "Rio das Ostras", state = "RJ", imageUrl = null, source = null,
    status = ModerationStatus.APPROVED, rejectionReason = null, curiosityCount = 0, createdBy = null,
)

private fun state(access: ChatAccess, distance: Double? = 87.0) = LandmarkDetailUiState(
    landmark = LANDMARK, distanceM = distance, chatAccess = access, loading = false,
)

@RunWith(RobolectricTestRunner::class)
class LandmarkDetailContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: LandmarkDetailUiState, onEnterChat: () -> Unit = {}, onEnablePrecise: () -> Unit = {}) {
        compose.setContent {
            HistTheme {
                LandmarkDetailContent(
                    state = state, errorMessage = null, onBack = {}, onEnterChat = onEnterChat,
                    onEnableLocation = {}, onEnablePrecise = onEnablePrecise, onRetry = {}, onLoadMore = {}, onOpenSuggestion = {},
                    onSuggestionText = {}, onSubmitSuggestion = {}, onDismissSuggestion = {},
                )
            }
        }
    }

    @Test fun `lista vazia mostra o estado vazio da spec`() {
        show(state(ChatAccess.Available))
        val empty = "Ninguém contou nada sobre este lugar ainda. Seja o primeiro."
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(empty))
        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText("Curiosidades · 0").assertExists()
    }

    @Test fun `Available mostra o botao habilitado que entra no chat`() {
        var entered = false
        show(state(ChatAccess.Available), onEnterChat = { entered = true })
        compose.onNodeWithText("Entrar no chat do local").assertIsEnabled().performClick()
        assertTrue(entered)
    }

    @Test fun `Locating mostra o botao desabilitado com Procurando sua localizacao`() {
        show(state(ChatAccess.Locating, distance = null))
        compose.onNodeWithText("Procurando sua localização…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun `TooFar mostra quantos metros faltam`() {
        show(state(ChatAccess.TooFar(210), 410.0))
        compose.onNodeWithText("Chegue a 200 m para entrar no chat (faltam 210 m)").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun `MockLocation explica que a localizacao simulada bloqueia o chat`() {
        show(state(ChatAccess.MockLocation))
        compose.onNodeWithText("Localização simulada detectada. Desative-a para entrar no chat.").assertIsDisplayed()
    }

    @Test fun `NoPermission mostra o convite e a acao de ativar`() {
        show(state(ChatAccess.NoPermission, null))
        compose.onNodeWithText("Ative a localização para entrar no chat.").assertIsDisplayed()
        compose.onNodeWithText("Ativar localização").assertIsDisplayed()
    }

    @Test fun `NeedsPrecise desabilita o botao e oferece pedir a localizacao precisa`() {
        var asked = 0
        show(state(ChatAccess.NeedsPrecise, null), onEnablePrecise = { asked++ })
        compose.onNodeWithText("Ative a localização precisa para entrar no chat.").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Ativar precisa").performClick()
        assertEquals(1, asked)
    }

    @Test fun `cabecalho mostra o total do ponto e nao so os itens carregados`() {
        show(state(ChatAccess.Available).copy(landmark = LANDMARK.copy(curiosityCount = 45)))
        compose.onNodeWithText("Curiosidades · 45").assertExists()
    }

    @Test fun `mensagem de encerramento fica visivel apos ser consumida e so consome depois de mostrar`() {
        var endMessage by mutableStateOf<Int?>(R.string.chat_out_of_range)
        var shown = 0
        val text = ApplicationProvider.getApplicationContext<Context>().getString(R.string.chat_out_of_range)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HistTheme {
                val snackbar = remember { SnackbarHostState() }
                EndMessageEffect(endMessage, snackbar) { shown++; endMessage = null }
                SnackbarHost(snackbar)
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(text).assertExists()
        assertEquals(0, shown)

        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(1, shown)
        compose.onNodeWithText(text).assertDoesNotExist()
    }
}
