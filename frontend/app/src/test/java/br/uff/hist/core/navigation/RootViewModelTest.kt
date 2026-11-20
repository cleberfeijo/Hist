package br.uff.hist.core.navigation

import br.uff.hist.core.model.User
import br.uff.hist.core.session.Session
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `finishLocationOnboarding marca o onboarding e leva a Main`() = runTest {
        val store = FakeSessionStore(Session("t", User("1", "A", "B", "a@b.c", emptySet())))
        val vm = RootViewModel(store)
        assertFalse(store.locationOnboardingDone.first())
        assertEquals(LocationOnboarding, vm.currentDestination())

        vm.finishLocationOnboarding()

        assertTrue(store.locationOnboardingDone.first())
        assertEquals(Main, vm.currentDestination())
    }
}
