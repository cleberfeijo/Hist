package br.uff.hist.testutil

import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.User
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSessionStore(initial: Session? = null) : SessionStore {
    private val _session = MutableStateFlow(initial)
    private val _theme = MutableStateFlow(ThemePreference.SYSTEM)
    private val _onboarding = MutableStateFlow(false)

    override val session: StateFlow<Session?> = _session
    override val theme = _theme
    override val locationOnboardingDone = _onboarding

    override fun currentToken(): String? = _session.value?.token

    override suspend fun awaitLoaded() = Unit

    override suspend fun save(session: Session) { _session.value = session }
    override suspend fun updateUser(user: User) { _session.value = _session.value?.copy(user = user) }
    override suspend fun replaceToken(token: String) { _session.value = _session.value?.copy(token = token) }
    override suspend fun clear() { _session.value = null }
    override suspend fun setTheme(p: ThemePreference) { _theme.value = p }
    override suspend fun setLocationOnboardingDone() { _onboarding.value = true }
}
