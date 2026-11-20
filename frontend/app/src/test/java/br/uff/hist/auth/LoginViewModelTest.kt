package br.uff.hist.auth

import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeAuthApi
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.httpError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val store = FakeSessionStore()
    private val vm = LoginViewModel(
        AuthRepository(api, ApiCaller(HistJson, SessionEvents()), store),
        ErrorMessages(ApplicationProvider.getApplicationContext()),
    )

    private fun fill() {
        vm.onEmailChange("ana@x.com")
        vm.onPasswordChange("senha1234")
    }

    @Test fun `sucesso salva a sessao e sinaliza done`() = runTest {
        fill()
        vm.submit()
        assertNotNull(store.session.value)
        assertEquals("tok", store.session.value!!.token)
        assertEquals("ana@x.com", store.session.value!!.user.email)
        assertTrue(vm.done.value)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test fun `invalid_credentials mostra a mensagem do servidor e nao desloga`() = runTest {
        api.loginHandler = { _, _ -> throw httpError(401, "invalid_credentials", "Email ou senha incorretos.") }
        fill()
        vm.submit()
        assertEquals("Email ou senha incorretos.", vm.uiState.value.error)
        assertNull(store.session.value)
        assertFalse(vm.done.value)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test fun `mensagem do servidor e truncada em 200 caracteres`() = runTest {
        api.loginHandler = { _, _ -> throw httpError(401, "invalid_credentials", "x".repeat(500)) }
        fill()
        vm.submit()
        assertEquals(200, vm.uiState.value.error!!.length)
    }

    @Test fun `sem internet mostra mensagem de conexao`() = runTest {
        api.loginHandler = { _, _ -> throw java.io.IOException("off") }
        fill()
        vm.submit()
        assertEquals("Sem conexão. Verifique sua internet.", vm.uiState.value.error)
    }

    @Test fun `submit duplo faz uma unica requisicao`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.loginHandler = { _, _ -> gate.await(); br.uff.hist.testutil.tokenResponse() }
        fill()
        vm.submit()
        assertTrue(vm.uiState.value.isSubmitting)
        vm.submit()
        gate.complete(Unit)
        assertEquals(1, api.loginCalls)
        assertTrue(vm.done.value)
    }

    @Test fun `email vazio nao chama a API`() = runTest {
        vm.onPasswordChange("senha1234")
        vm.submit()
        assertEquals(0, api.loginCalls)
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun `senha vazia nao chama a API`() = runTest {
        vm.onEmailChange("ana@x.com")
        vm.submit()
        assertEquals(0, api.loginCalls)
        assertNotNull(vm.uiState.value.error)
    }
}
