package br.uff.hist.auth

import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeAuthApi
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.httpError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val api = FakeAuthApi()
    private val store = FakeSessionStore(Session("old", User("1", "A", "B", "a@b.c", emptySet())))

    private fun repo(scope: CoroutineScope) = AuthRepository(api, ApiCaller(HistJson, SessionEvents()), store, scope)

    @Test fun `logout limpa a sessao antes de a rede responder`() = runTest {
        val gate = CompletableDeferred<Response<Unit>>()
        api.logoutHandler = { gate.await() }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))

        val result = repo.logout()

        assertTrue(result is ApiResult.Success)
        assertNull(store.session.value) // o servidor ainda nem foi chamado/respondeu
        advanceUntilIdle()
        assertEquals(1, api.logoutCalls)
        gate.complete(Response.success(Unit))
    }

    @Test fun `logout avisa o servidor com o token que acabou de ser apagado`() = runTest {
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        advanceUntilIdle()
        assertEquals("Bearer old", api.lastLogoutAuthorization)
    }

    @Test fun `logout limpa a sessao mesmo quando a API falha`() = runTest {
        api.logoutHandler = { throw httpError(500, "internal", "erro") }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        advanceUntilIdle()
        assertNull(store.session.value)
        assertEquals(3, api.logoutCalls) // 5xx é passageiro: 3 tentativas
    }

    @Test fun `logout limpa a sessao sem internet`() = runTest {
        api.logoutHandler = { throw java.io.IOException("off") }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        advanceUntilIdle()
        assertNull(store.session.value)
    }

    @Test fun `401 do token antigo nao derruba a sessao nova nem avisa expiracao`() = runTest {
        val events = SessionEvents()
        var expired = 0
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { events.expired.collect { expired++ } }
        api.logoutHandler = { throw httpError(401, "unauthorized", "token inválido") }
        val repo = AuthRepository(api, ApiCaller(HistJson, events), store, CoroutineScope(StandardTestDispatcher(testScheduler)))

        repo.logout()
        val fresh = Session("novo", User("2", "C", "D", "c@d.e", emptySet()))
        store.save(fresh)
        advanceUntilIdle()

        assertEquals(fresh, store.session.value)
        assertEquals(0, expired)
        assertEquals(1, api.logoutCalls) // resposta HTTP: sem nova tentativa
        collector.cancel()
    }

    @Test fun `falha de rede repete ate 3 vezes com espera de 2s e 4s`() = runTest {
        api.logoutHandler = { throw java.io.IOException("off") }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        runCurrent()
        assertEquals(1, api.logoutCalls)
        advanceTimeBy(1_999); runCurrent()
        assertEquals(1, api.logoutCalls)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, api.logoutCalls)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(3, api.logoutCalls)
        advanceUntilIdle()
        assertEquals(3, api.logoutCalls)
        assertEquals("Bearer old", api.lastLogoutAuthorization)
    }

    @Test fun `503 e depois 204 faz duas chamadas`() = runTest {
        var n = 0
        api.logoutHandler = {
            if (n++ == 0) Response.error(503, "".toResponseBody(null)) else Response.success(204, Unit)
        }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        advanceUntilIdle()
        assertEquals(2, api.logoutCalls)
    }

    @Test fun `429 repete e 401 como resposta nao repete`() = runTest {
        api.logoutHandler = { Response.error(429, "".toResponseBody(null)) }
        repo(CoroutineScope(StandardTestDispatcher(testScheduler))).logout()
        advanceUntilIdle()
        assertEquals(3, api.logoutCalls)
    }

    @Test fun `resposta 401 devolvida como Response nao repete`() = runTest {
        api.logoutHandler = { Response.error(401, "".toResponseBody(null)) }
        repo(CoroutineScope(StandardTestDispatcher(testScheduler))).logout()
        advanceUntilIdle()
        assertEquals(1, api.logoutCalls)
    }

    @Test fun `repeticao para quando a rede volta`() = runTest {
        var n = 0
        api.logoutHandler = { if (n++ == 0) throw java.io.IOException("off") else Response.success(Unit) }
        val repo = repo(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.logout()
        advanceUntilIdle()
        assertEquals(2, api.logoutCalls)
    }
}
