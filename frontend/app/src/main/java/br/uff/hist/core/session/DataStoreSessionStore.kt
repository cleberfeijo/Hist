package br.uff.hist.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import br.uff.hist.core.network.HistJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "hist_session")

@Serializable
private data class StoredUser(
    val id: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val roles: List<String> = emptyList(),
)

private fun User.toStored() = StoredUser(id, name, lastName, email, roles.map { it.name })

private fun StoredUser.toDomain() = User(
    id = id,
    name = name,
    lastName = lastName,
    email = email,
    roles = roles.mapNotNull { r -> Role.entries.firstOrNull { it.name == r } }.toSet(),
)

private object Keys {
    val TOKEN = stringPreferencesKey("token")
    val USER = stringPreferencesKey("user_json")
    val THEME = stringPreferencesKey("theme")
    val ONBOARDING = booleanPreferencesKey("location_onboarding_done")
}

private fun Preferences.toSession(): Session? {
    val token = this[Keys.TOKEN] ?: return null
    val json = this[Keys.USER] ?: return null
    val user = runCatching { HistJson.decodeFromString<StoredUser>(json).toDomain() }.getOrNull()
        ?: return null
    return Session(token, user)
}

@Singleton
class DataStoreSessionStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SessionStore {

    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(context.sessionDataStore, scope)

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                dataStore.data.map { it.toSession() }.collect {
                    _session.value = it
                    loaded.complete(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loaded.completeExceptionally(e)
            }
        }
    }

    /** Suspende até a primeira leitura do disco ter sido publicada em [session]. */
    suspend fun awaitLoaded() = loaded.await()

    override val theme: Flow<ThemePreference> = dataStore.data
        .map { prefs ->
            ThemePreference.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemePreference.SYSTEM
        }
        .distinctUntilChanged()

    override val locationOnboardingDone: Flow<Boolean> = dataStore.data
        .map { it[Keys.ONBOARDING] ?: false }
        .distinctUntilChanged()

    override fun currentToken(): String? = _session.value?.token

    override suspend fun save(session: Session) {
        dataStore.edit {
            it[Keys.TOKEN] = session.token
            it[Keys.USER] = HistJson.encodeToString(session.user.toStored())
        }
        awaitPublished(session)
    }

    override suspend fun updateUser(user: User) {
        val current = currentSessionFromDisk() ?: return
        save(current.copy(user = user))
    }

    override suspend fun replaceToken(token: String) {
        val current = currentSessionFromDisk() ?: return
        save(current.copy(token = token))
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(Keys.TOKEN)
            it.remove(Keys.USER)
        }
        awaitPublished(null)
    }

    override suspend fun setTheme(p: ThemePreference) {
        dataStore.edit { it[Keys.THEME] = p.name }
    }

    override suspend fun setLocationOnboardingDone() {
        dataStore.edit { it[Keys.ONBOARDING] = true }
    }

    private suspend fun currentSessionFromDisk(): Session? = dataStore.data.first().toSession()

    /** Garante que [session] (e portanto [currentToken]) já reflete a escrita ao retornar. */
    private suspend fun awaitPublished(expected: Session?) {
        _session.first { it == expected }
    }
}
