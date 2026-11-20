package br.uff.hist.core.navigation

import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.core.session.SessionExpiryHandler
import br.uff.hist.core.session.SessionStore
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootDestinationTest {
    @get:Rule val main = MainDispatcherRule()

    private val session = Session("t", User("1", "A", "B", "a@b.c", emptySet()))

    @Test fun `sem sessao vai para Login`() = assertEquals(Login, rootDestination(null, false))

    @Test fun `sem sessao ignora onboarding`() = assertEquals(Login, rootDestination(null, true))

    @Test fun `com sessao sem onboarding vai para LocationOnboarding`() =
        assertEquals(LocationOnboarding, rootDestination(session, false))

    @Test fun `com sessao e onboarding vai para Main`() =
        assertEquals(Main, rootDestination(session, true))

    @Test fun `sessao revogada limpa a sessao e leva ao Login`() = runTest {
        val store = FakeSessionStore(session)
        val events = SessionEvents()
        SessionExpiryHandler(events, store, TestScope(UnconfinedTestDispatcher(testScheduler))).start()
        assertEquals(Main, rootDestination(store.session.value, true))

        events.notifyExpired()

        assertNull(store.session.value)
        assertEquals(Login, rootDestination(store.session.value, true))
    }

    @Test fun `RootViewModel so escolhe o destino inicial depois de carregar`() = runTest {
        val loaded = CompletableDeferred<Unit>()
        val fake = FakeSessionStore(session)
        val store = object : SessionStore by fake {
            override suspend fun awaitLoaded() = loaded.await()
        }
        val vm = RootViewModel(store)
        assertNull(vm.startDestination.value)

        fake.setLocationOnboardingDone()
        loaded.complete(Unit)

        assertEquals(Main, vm.startDestination.value)
    }

    @Test fun `RootViewModel sem sessao comeca no Login`() = runTest {
        val vm = RootViewModel(FakeSessionStore())
        assertEquals(Login, vm.startDestination.value)
    }
}
