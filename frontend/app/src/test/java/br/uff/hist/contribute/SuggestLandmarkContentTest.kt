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
}
