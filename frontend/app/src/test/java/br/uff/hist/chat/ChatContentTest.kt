package br.uff.hist.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import br.uff.hist.core.designsystem.HistTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun chatState(
    phase: ChatPhase = ChatPhase.Active,
    memberCount: Int? = null,
    items: List<ChatItem> = emptyList(),
    distanceM: Double? = null,
    warnLeaving: Boolean = false,
    maxMessageLength: Int = 1000,
    notice: String? = null,
) = ChatState(phase, "Igreja Matriz", memberCount, items, distanceM, warnLeaving, 200, 300, maxMessageLength, notice)

@RunWith(RobolectricTestRunner::class)
class ChatContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        state: ChatState,
        onSend: (String) -> SendResult = { SendResult.SENT },
        onNoticeShown: () -> Unit = {},
    ) {
        compose.setContent {
            HistTheme {
                ChatContent(state = state, onSend = onSend, onBack = {}, onNoticeShown = onNoticeShown)
            }
        }
    }

    @Test fun `sem memberCount nao mostra aqui agora`() {
        show(chatState(memberCount = null))
        compose.onNodeWithText("aqui agora", substring = true).assertDoesNotExist()
    }

    @Test fun `memberCount 3 e 1 usam o plural certo`() {
        show(chatState(memberCount = 3))
        compose.onNodeWithText("3 pessoas aqui agora").assertExists()
    }

    @Test fun `memberCount 1 usa o singular`() {
        show(chatState(memberCount = 1))
        compose.onNodeWithText("1 pessoa aqui agora").assertExists()
    }

    @Test fun `aviso efemero sempre presente`() {
        show(chatState())
        compose.onNodeWithText("As mensagens só existem enquanto você estiver aqui. Ao sair do local, a conversa some.").assertExists()
    }

    @Test fun `afastando mostra a faixa com a distancia`() {
        show(chatState(warnLeaving = true, distanceM = 260.0))
        compose.onNodeWithText("Você está se afastando (260 m). A partir de 300 m você sai do chat.").assertExists()
    }

    @Test fun `sem warnLeaving nao mostra a faixa`() {
        show(chatState(warnLeaving = false, distanceM = 260.0))
        compose.onNodeWithText("Você está se afastando", substring = true).assertDoesNotExist()
    }

    @Test fun `Reconnecting mostra a faixa`() {
        show(chatState(phase = ChatPhase.Reconnecting))
        compose.onNodeWithText("Reconectando…").assertExists()
    }

    @Test fun `mensagens e presencas aparecem`() {
        show(
            chatState(
                items = listOf(
                    ChatItem.Msg("m1", "Bia", "Oi pessoal", mine = false, sentAt = Instant.EPOCH),
                    ChatItem.Msg("m2", "Ana", "Olá!", mine = true, sentAt = Instant.EPOCH),
                    ChatItem.PresenceNote("p1", "Caio", joined = true),
                    ChatItem.PresenceNote("p2", "Duda", joined = false),
                ),
            ),
        )
        compose.onNodeWithText("Bia").assertExists()
        compose.onNodeWithText("Oi pessoal").assertExists()
        compose.onNodeWithText("Olá!").assertExists()
        compose.onNodeWithText("Caio entrou").assertExists()
        compose.onNodeWithText("Duda saiu").assertExists()
    }

    @Test fun `enviar fica desabilitado vazio e habilitado com texto, e limpa apos SENT`() {
        val sent = mutableListOf<String>()
        show(chatState(), onSend = { sent += it; SendResult.SENT })
        compose.onNodeWithText("Enviar").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("oi")
        compose.onNodeWithText("Enviar").assertIsEnabled().performClick()
        assertEquals(listOf("oi"), sent)
        compose.onNodeWithText("oi").assertDoesNotExist()
    }

    @Test fun `texto so com espacos mantem o botao desabilitado`() {
        show(chatState())
        compose.onNode(hasSetTextAction()).performTextInput("   ")
        compose.onNodeWithText("Enviar").assertIsNotEnabled()
    }

    @Test fun `contador aparece a partir de max menos 100 e bloqueia acima do maximo`() {
        show(chatState(maxMessageLength = 120))
        compose.onNode(hasSetTextAction()).performTextInput("a".repeat(19))
        compose.onNodeWithText("19/120").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("a")
        compose.onNodeWithText("20/120").assertExists()
        compose.onNodeWithText("Enviar").assertIsEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("a".repeat(101))
        compose.onNodeWithText("121/120").assertExists()
        compose.onNodeWithText("Enviar").assertIsNotEnabled()
    }

    @Test fun `emoji conta como um caractere`() {
        show(chatState(maxMessageLength = 3))
        compose.onNode(hasSetTextAction()).performTextInput("😀😀😀")
        compose.onNodeWithText("Enviar").assertIsEnabled()
    }

    @Test fun `fora de Active o envio fica desabilitado`() {
        show(chatState(phase = ChatPhase.Reconnecting))
        compose.onNode(hasSetTextAction()).performTextInput("oi")
        compose.onNodeWithText("Enviar").assertIsNotEnabled()
    }

    @Test fun `notice vira snackbar e e consumida`() {
        var shown = 0
        show(chatState(notice = "Devagar!"), onNoticeShown = { shown++ })
        compose.onNodeWithText("Devagar!").assertExists()
        compose.waitForIdle()
        assertEquals(1, shown)
    }
}

