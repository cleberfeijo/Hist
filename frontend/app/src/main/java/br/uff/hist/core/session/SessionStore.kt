package br.uff.hist.core.session

import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.User
import br.uff.hist.core.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class Session(val token: String, val user: User)

interface SessionStore : TokenProvider {
    val session: StateFlow<Session?>
    val theme: Flow<ThemePreference>
    val locationOnboardingDone: Flow<Boolean>

    suspend fun save(session: Session)
    suspend fun updateUser(user: User)
    suspend fun replaceToken(token: String)
    suspend fun clear()
    suspend fun setTheme(p: ThemePreference)
    suspend fun setLocationOnboardingDone()
}
