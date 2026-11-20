package br.uff.hist.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    @Test fun `replaceToken e updateUser apos clear nao ressuscitam a sessao`() = runTest {
        val store = newStore()
        store.save(Session("t1", user))
        store.clear()
        store.replaceToken("t2")
        store.updateUser(user.copy(name = "Outro"))
        assertNull(store.session.value)
        assertNull(store.currentToken())
        assertNull(newStore().session.value)
    }

    @Test fun `save e clear concorrentes terminam e cache bate com o disco`() = runTest {
        val store = newStore()
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                repeat(50) { i ->
                    listOf(
                        launch { store.save(Session("t$i", user)) },
                        launch { store.clear() },
                        launch { store.replaceToken("r$i") },
                    ).joinAll()
                }
            }
        }
        val inMemory = store.session.value
        assertEquals(inMemory, newStore().session.value)
    }

    @Test fun `tema e onboarding persistem em nova instancia`() = runTest {
        val store = newStore()
        store.setTheme(ThemePreference.LIGHT)
        store.setLocationOnboardingDone()
        val other = newStore()
        assertEquals(ThemePreference.LIGHT, other.theme.first())
        assertEquals(true, other.locationOnboardingDone.first())
    }

    @Test fun `erro de IO na leitura nao quebra o store`() = runTest {
        val scope = CoroutineScope(Job() + Dispatchers.IO).also { scopes += it }
        val broken = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("disco") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(emptyPreferences())
        }
        val store = DataStoreSessionStore(broken, scope)
        withTimeout(10_000) { store.awaitLoaded() }
        assertNull(store.session.value)
        assertEquals(ThemePreference.SYSTEM, store.theme.first())
        assertFalse(store.locationOnboardingDone.first())
        store.save(Session("t1", user))
        assertEquals("t1", store.currentToken())
    }

    @Test fun `clear derruba o token da memoria mesmo se a escrita falhar`() = runTest {
        val scope = CoroutineScope(Job() + Dispatchers.IO).also { scopes += it }
        var failWrites = false
        val flaky = object : DataStore<Preferences> {
            private var current = emptyPreferences()
            override val data = flow { emit(current) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failWrites) throw IOException("disco cheio")
                current = transform(current)
                return current
            }
        }
        val store = DataStoreSessionStore(flaky, scope)
        store.save(Session("t1", user))
        assertEquals("t1", store.currentToken())
        failWrites = true
        runCatching { store.clear() }
        assertNull(store.currentToken())
        assertNull(store.session.value)
    }
}
