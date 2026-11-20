package br.uff.hist.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.designsystem.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: SettingsUiState, calls: MutableList<String> = mutableListOf()) {
        compose.setContent {
            HistTheme {
                SettingsContent(
                    state = state,
                    actions = SettingsActions(
                        onBack = {}, onTheme = { calls += "theme:$it" }, onOpenDialog = { calls += "open:$it" },
                        onLogout = { calls += "logout" }, onCloseDialog = {}, onName = {}, onLastName = {},
                        onEmail = {}, onCurrentPassword = {}, onNewPassword = {}, onSubmit = { calls += "submit" },
                    ),
                )
            }
        }
    }

    @Test fun `mostra as secoes e escolhe tema`() {
        val calls = mutableListOf<String>()
        show(SettingsUiState(), calls)
        compose.onNodeWithText("Aparência").assertExists()
        compose.onNodeWithText("Escuro").performClick()
        compose.onNodeWithText("Sair").performScrollTo().performClick()
        compose.onNodeWithText("Excluir conta").performScrollTo().performClick()
        assertEquals(listOf("theme:DARK", "logout", "open:DELETE_ACCOUNT"), calls)
    }

    @Test fun `dialogo de excluir conta mostra aviso e campo de senha`() {
        show(SettingsUiState(dialog = SettingsDialog.DELETE_ACCOUNT))
        compose.onNodeWithText("Excluir sua conta?").assertExists()
        compose.onNodeWithText("Senha atual").assertExists()
    }

    @Test fun `erro de campo aparece no campo da senha atual`() {
        show(
            SettingsUiState(
                dialog = SettingsDialog.CHANGE_PASSWORD,
                form = SettingsForm(fieldErrors = mapOf(SettingsFields.CURRENT_PASSWORD to "Senha incorreta.")),
            ),
        )
        compose.onNodeWithText("Senha incorreta.").assertExists()
        compose.onNodeWithText("Nova senha").assertExists()
    }
}
