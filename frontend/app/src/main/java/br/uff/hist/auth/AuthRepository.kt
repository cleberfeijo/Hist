package br.uff.hist.auth

import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.RegisterRequest
import br.uff.hist.core.network.TokenResponseDto
import br.uff.hist.core.network.map
import br.uff.hist.core.network.toDomain
import br.uff.hist.core.session.ApplicationScope
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository @Inject constructor(
    private val api: HistApi,
    private val caller: ApiCaller,
    private val store: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun login(email: String, password: String): ApiResult<Unit> =
        caller.call { saveSession(api.login(email, password)) }

    suspend fun register(name: String, lastName: String, email: String, password: String): ApiResult<Unit> =
        caller.call { saveSession(api.register(RegisterRequest(name, lastName, email, password))) }

    /**
     * Limpa a sessão local primeiro (o "Sair" não espera a rede) e só então avisa o servidor, em segundo plano e
     * sem tratar falha: o token já não vale localmente.
     */
    suspend fun logout(): ApiResult<Unit> {
        val token = store.currentToken()
        withContext(NonCancellable) { store.clear() }
        if (!token.isNullOrEmpty()) {
            scope.launch { caller.call { api.logout("Bearer $token") } }
        }
        return ApiResult.Success(Unit)
    }

    private suspend fun saveSession(token: TokenResponseDto) {
        store.save(Session(token.accessToken, token.user.toDomain()))
    }
}
