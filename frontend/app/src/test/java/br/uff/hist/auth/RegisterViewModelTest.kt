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
class RegisterViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val store = FakeSessionStore()
    private val vm = RegisterViewModel(
        AuthRepository(api, ApiCaller(HistJson, SessionEvents()), store),
        ErrorMessages(ApplicationProvider.getApplicationContext()),
    )

    private fun fill(password: String = "senha1234") {
        vm.onNameChange("Ana")
        vm.onLastNameChange("Silva")
        vm.onEmailChange("ana@x.com")
        vm.onPasswordChange(password)
    }

    @Test fun `sucesso salva a sessao e sinaliza done`() = runTest {
        fill()
        vm.submit()
        assertNotNull(store.session.value)
        assertTrue(vm.done.value)
        assertEquals("Silva", api.lastRegister!!.lastName)
    }

    @Test fun `senha com 7 caracteres da erro local sem chamada`() = runTest {
        fill("1234567")
        vm.submit()
        assertNotNull(vm.uiState.value.fieldErrors["password"])
        assertEquals(0, api.registerCalls)
    }

    @Test fun `senha com 8 caracteres e aceita`() = runTest {
        fill("12345678")
        vm.submit()
        assertEquals(1, api.registerCalls)
    }

    @Test fun `senha com 73 caracteres da erro local sem chamada`() = runTest {
        fill("a".repeat(73))
        vm.submit()
        assertNotNull(vm.uiState.value.fieldErrors["password"])
        assertEquals(0, api.registerCalls)
    }

    @Test fun `senha com 72 caracteres e aceita`() = runTest {
        fill("a".repeat(72))
        vm.submit()
        assertEquals(1, api.registerCalls)
    }

    @Test fun `campos vazios dao erro local sem chamada`() = runTest {
        vm.submit()
        val errors = vm.uiState.value.fieldErrors
        assertTrue(errors.keys.containsAll(listOf("name", "last_name", "email", "password")))
        assertEquals(0, api.registerCalls)
    }

    @Test fun `email_taken 409 vira erro no campo email`() = runTest {
        api.registerHandler = { throw httpError(409, "email_taken", "Este e-mail já está em uso.") }
        fill()
        vm.submit()
        assertEquals("Este e-mail já está em uso.", vm.uiState.value.fieldErrors["email"])
        assertNull(store.session.value)
        assertFalse(vm.done.value)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test fun `422 com details vira fieldErrors`() = runTest {
        api.registerHandler = {
            throw httpError(
                422, "validation_error", "Dados inválidos.",
                """[{"field":"email","message":"e-mail inválido"},{"field":"last_name","message":"curto"}]""",
            )
        }
        fill()
        vm.submit()
        assertEquals(
            mapOf("email" to "e-mail inválido", "last_name" to "curto"),
            vm.uiState.value.fieldErrors,
        )
    }

    @Test fun `erro de rede vai para error`() = runTest {
        api.registerHandler = { throw java.io.IOException("off") }
        fill()
        vm.submit()
        assertEquals("Sem conexão. Verifique sua internet.", vm.uiState.value.error)
    }

    @Test fun `submit duplo faz uma unica requisicao`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.registerHandler = { gate.await(); br.uff.hist.testutil.tokenResponse() }
        fill()
        vm.submit()
        vm.submit()
        gate.complete(Unit)
        assertEquals(1, api.registerCalls)
    }
}
