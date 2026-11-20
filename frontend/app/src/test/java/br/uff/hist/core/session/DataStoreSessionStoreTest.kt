package br.uff.hist.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DataStoreSessionStoreTest {
    private val user = User("u1", "Ana", "Silva", "ana@x.com", setOf(Role.USER, Role.MODERATOR))
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before fun setUp() {
        file = File.createTempFile("hist_session", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    /** Simula reiniciar o processo: encerra as instâncias anteriores (um arquivo, um DataStore ativo). */
    private suspend fun newStore(): DataStoreSessionStore {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        scopes.clear()
        val scope = CoroutineScope(Job() + Dispatchers.IO).also { scopes += it }
        val ds = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreSessionStore(ds, scope).also { it.awaitLoaded() }
    }

    @Test fun `sessao salva sobrevive a nova instancia`() = runTest {
        newStore().save(Session("t1", user))
        val other = newStore()
        assertEquals("t1", other.session.value?.token)
        assertEquals(user, other.session.value?.user)
        assertEquals("t1", other.currentToken())
    }

    @Test fun `replaceToken troca o token e preserva o usuario`() = runTest {
        val store = newStore()
        store.save(Session("t1", user))
        store.replaceToken("t2")
        assertEquals("t2", store.currentToken())
        assertEquals(user, store.session.value?.user)
        assertEquals("t2", newStore().currentToken())
    }

    @Test fun `updateUser troca o usuario e preserva o token`() = runTest {
        val store = newStore()
        store.save(Session("t1", user))
        val renamed = user.copy(name = "Beatriz")
        store.updateUser(renamed)
        assertEquals(Session("t1", renamed), store.session.value)
    }

    @Test fun `clear remove sessao mas preserva tema`() = runTest {
        val store = newStore()
        store.save(Session("t1", user))
        store.setTheme(ThemePreference.DARK)
        store.clear()
        assertNull(store.session.value)
        assertNull(store.currentToken())
        assertEquals(ThemePreference.DARK, store.theme.first())
        assertNull(newStore().session.value)
    }

    @Test fun `padroes de tema e onboarding`() = runTest {
        val store = newStore()
        assertEquals(ThemePreference.SYSTEM, store.theme.first())
        assertFalse(store.locationOnboardingDone.first())
        store.setLocationOnboardingDone()
        assertEquals(true, store.locationOnboardingDone.first())
    }

    @Test fun `replaceToken sem sessao nao cria sessao`() = runTest {
        val store = newStore()
        store.replaceToken("x")
        assertNull(store.session.value)
    }
}
