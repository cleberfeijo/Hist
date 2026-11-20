package br.uff.hist.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.UserDto
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeAccountApi
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val compose = createComposeRule()

    private val api = FakeAccountApi()
    private val store = FakeSessionStore(Session("tok", User("1", "Ana", "Silva", "ana@x.com", setOf(Role.USER))))

    private fun vm() = ProfileViewModel(AccountRepository(api, ApiCaller(HistJson, SessionEvents()), store))

    @Test fun `refresh atualiza os papeis na sessao e Moderacao aparece`() = runTest(dispatcher) {
        api.meHandler = { UserDto("1", "Ana", "Silva", "ana@x.com", listOf("user", "moderator")) }
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        val user = store.session.value!!.user
        assertEquals(setOf(Role.USER, Role.MODERATOR), user.roles)
        compose.setContent {
            HistTheme { ProfileContent(user, onMyContributions = {}, onModeration = {}, onSettings = {}) }
        }
        compose.onNodeWithText("Moderação").assertExists()
    }

    @Test fun `falha no refresh e ignorada e a sessao fica`() = runTest(dispatcher) {
        api.meHandler = { throw IOException() }
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(setOf(Role.USER), store.session.value!!.user.roles)
    }
}
