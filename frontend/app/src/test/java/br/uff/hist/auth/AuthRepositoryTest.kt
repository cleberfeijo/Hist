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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    private val api = FakeAuthApi()
    private val store = FakeSessionStore(Session("old", User("1", "A", "B", "a@b.c", emptySet())))
    private val repo = AuthRepository(api, ApiCaller(HistJson, SessionEvents()), store)

    @Test fun `logout limpa a sessao mesmo quando a API falha`() = runTest {
        api.logoutHandler = { throw httpError(500, "internal", "erro") }
        val result = repo.logout()
        assertTrue(result is ApiResult.Failure)
        assertNull(store.session.value)
        assertEquals(1, api.logoutCalls)
    }

    @Test fun `logout limpa a sessao sem internet`() = runTest {
        api.logoutHandler = { throw java.io.IOException("off") }
        repo.logout()
        assertNull(store.session.value)
    }

    @Test fun `logout com sucesso limpa a sessao`() = runTest {
        assertTrue(repo.logout() is ApiResult.Success)
        assertNull(store.session.value)
    }
}
