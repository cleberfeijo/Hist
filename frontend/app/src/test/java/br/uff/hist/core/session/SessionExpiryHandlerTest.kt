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
}
