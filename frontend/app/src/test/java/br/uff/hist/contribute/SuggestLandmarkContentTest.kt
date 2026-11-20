package br.uff.hist.contribute

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.RIO_DAS_OSTRAS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuggestLandmarkContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: SuggestLandmarkUiState) = compose.setContent {
        HistTheme {
            SuggestLandmarkContent(
                state = state, onBack = {}, onNameChange = {}, onCategoryChange = {},
                onDescriptionChange = {}, onCuriosityChange = {}, onSubmit = {}, mapContent = {},
            )
        }
    }

    @Test fun `erro de categoria obrigatoria aparece`() {
        show(SuggestLandmarkUiState(pin = RIO_DAS_OSTRAS, fieldErrors = mapOf("category" to "Escolha uma categoria.")))
        compose.onNodeWithText("Escolha uma categoria.").assertExists()
        compose.onNodeWithText("Enviar sugestão").assertIsEnabled()
    }

    @Test fun `botao fica desabilitado enquanto envia`() {
        show(SuggestLandmarkUiState(pin = GeoPoint(0.0, 0.0), submitting = true))
        compose.onNodeWithText("Enviar sugestão").assertIsNotEnabled()
    }

    @Test fun `erro de nome aparece`() {
        show(SuggestLandmarkUiState(pin = RIO_DAS_OSTRAS, fieldErrors = mapOf("name" to "O nome precisa ter entre 2 e 200 caracteres.")))
        compose.onNodeWithText("O nome precisa ter entre 2 e 200 caracteres.").assertExists()
    }

    @Test fun `erro geral aparece`() {
        show(SuggestLandmarkUiState(pin = RIO_DAS_OSTRAS, error = "fora do intervalo"))
        compose.onNodeWithText("fora do intervalo").assertExists()
    }

    @Test fun `botao fica desabilitado sem pino`() {
        show(SuggestLandmarkUiState(pin = null))
        compose.onNodeWithText("Enviar sugestão").assertIsNotEnabled()
    }
}
