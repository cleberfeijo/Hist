package br.uff.hist.core.session

import br.uff.hist.core.model.User
import br.uff.hist.testutil.FakeSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiryHandlerTest {
    @Test fun `sessao expirada limpa o armazenamento`() = runTest {
        val store = FakeSessionStore(Session("t", User("1", "A", "B", "a@b.c", emptySet())))
        val events = SessionEvents()
        SessionExpiryHandler(events, store, TestScope(UnconfinedTestDispatcher(testScheduler))).start()
        assertNotNull(store.session.value)

        events.notifyExpired()

        assertNull(store.session.value)
    }

    @Test fun `falha no primeiro clear nao derruba o coletor`() = runTest {
        val fake = FakeSessionStore(Session("t", User("1", "A", "B", "a@b.c", emptySet())))
        var calls = 0
        val store = object : SessionStore by fake {
            override suspend fun clear() {
                if (calls++ == 0) throw IllegalStateException("disco") else fake.clear()
            }
        }
        val events = SessionEvents()
        SessionExpiryHandler(events, store, TestScope(UnconfinedTestDispatcher(testScheduler))).start()

        events.notifyExpired()
        assertNotNull(fake.session.value)
        events.notifyExpired()

        assertNull(fake.session.value)
    }
}
