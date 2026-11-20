package br.uff.hist.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun user(vararg roles: Role) = User("1", "Ana", "Silva", "ana@x.com", roles.toSet())

@RunWith(RobolectricTestRunner::class)
class ProfileContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(user: User, calls: MutableList<String> = mutableListOf()) {
        compose.setContent {
            HistTheme {
                ProfileContent(
                    user = user,
                    onMyContributions = { calls += "contrib" },
                    onModeration = { calls += "moderation" },
                    onSettings = { calls += "settings" },
                )
            }
        }
    }

    @Test fun `usuario comum nao ve Moderacao`() {
        show(user(Role.USER))
        compose.onNodeWithText("Minhas contribuições").assertExists()
        compose.onNodeWithText("Configurações").assertExists()
        compose.onNodeWithText("Moderação").assertDoesNotExist()
    }

    @Test fun `moderador ve Moderacao`() {
        show(user(Role.MODERATOR))
        compose.onNodeWithText("Moderação").assertExists()
    }

    @Test fun `mostra nome e e-mail e os itens navegam`() {
        val calls = mutableListOf<String>()
        show(user(Role.MODERATOR), calls)
        compose.onNodeWithText("Ana Silva").assertExists()
        compose.onNodeWithText("ana@x.com").assertExists()
        compose.onNodeWithText("Minhas contribuições").performClick()
        compose.onNodeWithText("Moderação").performClick()
        compose.onNodeWithText("Configurações").performClick()
        assertEquals(listOf("contrib", "moderation", "settings"), calls)
    }
}
