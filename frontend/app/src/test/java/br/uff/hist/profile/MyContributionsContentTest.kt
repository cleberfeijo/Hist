package br.uff.hist.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.network.toDomain
import br.uff.hist.testutil.curiosityDto
import br.uff.hist.testutil.landmarkDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyContributionsContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: MyContributionsUiState, asked: MutableList<DeleteTarget> = mutableListOf()) {
        compose.setContent {
            HistTheme {
                MyContributionsContent(
                    state = state, onBack = {}, onLoadMoreLandmarks = {}, onLoadMoreCuriosities = {},
                    onAskDelete = { asked += it }, onConfirmDelete = {}, onCancelDelete = {},
                )
            }
        }
    }

    private fun loaded(vararg l: br.uff.hist.core.model.Landmark) =
        MyContributionsUiState(landmarks = ListState(items = l.toList(), loaded = true), curiosities = ListState(loaded = true))

    @Test fun `rejeitado mostra selo e motivo e permite excluir`() {
        val asked = mutableListOf<DeleteTarget>()
        show(loaded(landmarkDto("a", "rejected", "duplicado").toDomain()), asked)
        compose.onNodeWithText("Rejeitado").assertExists()
        compose.onNodeWithText("Motivo: duplicado").assertExists()
        compose.onNodeWithContentDescription("Excluir").performClick()
        assertEquals(listOf<DeleteTarget>(DeleteTarget.OfLandmark("a")), asked)
    }

    @Test fun `aprovado nao tem botao de excluir`() {
        show(loaded(landmarkDto("a", "approved").toDomain()))
        compose.onNodeWithText("Aprovado").assertExists()
        compose.onNodeWithContentDescription("Excluir").assertDoesNotExist()
    }

    @Test fun `lista vazia mostra o estado vazio`() {
        show(loaded())
        compose.onNodeWithText("Você ainda não enviou nenhuma contribuição.").assertExists()
    }

    @Test fun `aba de curiosidades lista as curiosidades com excluir`() {
        val state = MyContributionsUiState(
            landmarks = ListState(loaded = true),
            curiosities = ListState(items = listOf(curiosityDto("c1").toDomain()), loaded = true),
        )
        show(state)
        compose.onNodeWithText("Curiosidades").performClick()
        compose.onNodeWithText("Curiosidade c1").assertExists()
        compose.onNodeWithContentDescription("Excluir").assertExists()
    }

    @Test fun `dialogo de confirmacao aparece com o alvo`() {
        show(loaded().copy(deleteTarget = DeleteTarget.OfLandmark("a")))
        compose.onNodeWithText("Excluir esta contribuição?").assertExists()
        compose.onNodeWithText("Cancelar").assertExists()
    }
}
