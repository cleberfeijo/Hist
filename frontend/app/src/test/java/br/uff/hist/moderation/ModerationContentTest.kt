package br.uff.hist.moderation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.network.toDomain
import br.uff.hist.profile.ListState
import br.uff.hist.testutil.curiosityDto
import br.uff.hist.testutil.landmarkDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModerationContentTest {
    @get:Rule val compose = createComposeRule()

    private fun showQueue(state: ModerationQueueUiState, opened: MutableList<String> = mutableListOf()) {
        compose.setContent {
            HistTheme {
                ModerationQueueContent(
                    state = state, onBack = {}, onOpenLandmark = { opened += "p:$it" }, onOpenCuriosity = { opened += "c:$it" },
                    onLoadMoreLandmarks = {}, onLoadMoreCuriosities = {},
                )
            }
        }
    }

    @Test fun `fila vazia mostra o estado vazio`() {
        showQueue(ModerationQueueUiState(landmarks = ListState(loaded = true), curiosities = ListState(loaded = true)))
        compose.onNodeWithText("Nada para revisar por aqui.").assertExists()
    }

    @Test fun `fila de pontos mostra o nome e abre o detalhe`() {
        val opened = mutableListOf<String>()
        showQueue(
            ModerationQueueUiState(landmarks = ListState(items = listOf(landmarkDto("a", "pending").toDomain()), loaded = true)),
            opened,
        )
        compose.onNodeWithText("Ponto a").performClick()
        assertEquals(listOf("p:a"), opened)
    }

    @Test fun `ponto importado mostra Importado e a data`() {
        val l = landmarkDto("a", "pending").copy(createdAt = "2020-01-01T10:00:00Z").toDomain()
        showQueue(ModerationQueueUiState(landmarks = ListState(items = listOf(l), loaded = true)))
        compose.onNodeWithText("Importado · ", substring = true).assertExists()
    }

    @Test fun `curiosidade da fila corta o trecho em 120 e mostra Importado sem autor`() {
        val long = "x".repeat(200)
        val c = curiosityDto("c1").copy(text = long).toDomain()
        showQueue(
            ModerationQueueUiState(
                landmarks = ListState(loaded = true),
                curiosities = ListState(items = listOf(c), loaded = true),
            ),
        )
        compose.onNodeWithText("Curiosidades").performClick()
        compose.onNodeWithText("x".repeat(120)).assertExists()
        compose.onNodeWithText("Importado", substring = true).assertExists()
    }

    private fun showDetail(
        state: ModerationDetailUiState,
        approved: MutableList<Unit> = mutableListOf(),
        confirmed: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            HistTheme {
                ModerationDetailContent(
                    state = state, onBack = {}, onApprove = { approved += Unit }, onAskReject = {},
                    onReasonChange = {}, onConfirmReject = { confirmed += Unit }, onDismissReject = {}, onRetry = {},
                    mapContent = { Text("mini-mapa") },
                )
            }
        }
    }

    @Test fun `detalhe de ponto mostra mapa, descricao e botoes`() {
        val l = landmarkDto("a", "pending").copy(description = "Igreja antiga", category = "igreja").toDomain()
        val approved = mutableListOf<Unit>()
        showDetail(ModerationDetailUiState(loading = false, landmark = l), approved)
        compose.onNodeWithText("mini-mapa").assertExists()
        compose.onNodeWithText("Igreja antiga").assertExists()
        compose.onNodeWithText("Igreja / templo").assertExists()
        compose.onNodeWithText("Aprovar").performClick()
        assertEquals(1, approved.size)
        compose.onNodeWithText("Rejeitar").assertExists()
    }

    @Test fun `detalhe de curiosidade mostra o nome do ponto`() {
        showDetail(ModerationDetailUiState(loading = false, curiosity = curiosityDto("c").toDomain()))
        compose.onNodeWithText("Ponto: Igreja").assertExists()
        compose.onNodeWithText("Curiosidade c").assertExists()
    }

    @Test fun `dialogo de rejeicao mostra erro local e mantem o motivo`() {
        val l = landmarkDto("a", "pending").toDomain()
        val confirmed = mutableListOf<Unit>()
        showDetail(
            ModerationDetailUiState(loading = false, landmark = l, rejectDialog = true, reason = "ok", reasonError = true),
            confirmed = confirmed,
        )
        compose.onNodeWithText("Motivo da rejeição").assertExists()
        compose.onNodeWithText("ok").assertExists()
        compose.onNodeWithText("O motivo precisa ter entre 3 e 500 caracteres.").assertExists()
        compose.onNodeWithText("Cancelar").assertExists()
        compose.onAllNodesWithText("Rejeitar").onLast().performClick()
        assertEquals(1, confirmed.size)
    }

    @Test fun `item indisponivel mostra aviso e volta`() {
        var back = 0
        compose.setContent {
            HistTheme {
                ModerationDetailContent(
                    state = ModerationDetailUiState(loading = false, unavailable = true), onBack = { back++ },
                    onApprove = {}, onAskReject = {}, onReasonChange = {}, onConfirmReject = {},
                    onDismissReject = {}, onRetry = {}, mapContent = {},
                )
            }
        }
        compose.onNodeWithText("Este item não está mais disponível.").assertExists()
        compose.onNodeWithText("Aprovar").assertDoesNotExist()
        compose.onNodeWithText("Voltar").performClick()
        assertEquals(1, back)
    }
}
