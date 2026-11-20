package br.uff.hist.auth

import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.RegisterRequest
import br.uff.hist.core.network.TokenResponseDto
import br.uff.hist.core.network.map
import br.uff.hist.core.network.toDomain
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AuthRepository @Inject constructor(
    private val api: HistApi,
    private val caller: ApiCaller,
    private val store: SessionStore,
) {
    suspend fun login(email: String, password: String): ApiResult<Unit> =
        caller.call { saveSession(api.login(email, password)) }

    suspend fun register(name: String, lastName: String, email: String, password: String): ApiResult<Unit> =
        caller.call { saveSession(api.register(RegisterRequest(name, lastName, email, password))) }

    /** Sempre limpa a sessão local, mesmo que o servidor não responda. */
    suspend fun logout(): ApiResult<Unit> {
        try {
            return caller.call { api.logout() }.map { }
        } finally {
            withContext(NonCancellable) { store.clear() }
        }
    }

    private suspend fun saveSession(token: TokenResponseDto) {
        store.save(Session(token.accessToken, token.user.toDomain()))
    }
}
