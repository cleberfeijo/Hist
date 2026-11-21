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
import retrofit2.HttpException
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Espera antes de cada nova tentativa: 3 tentativas no total (a primeira é imediata; o último valor é só sentinela). */
private val LOGOUT_RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 0L)

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
            scope.launch { revokeOnServer(token) }
        }
        return ApiResult.Success(Unit)
    }

    /**
     * Revogação "melhor esforço": direto na API (sem [ApiCaller]) para que um 401 do token antigo nunca dispare
     * `notifyExpired()` e derrube uma sessão nova. Repete (até 3 tentativas) em falha de rede, 5xx e 429; qualquer outra resposta encerra.
     */
    private suspend fun revokeOnServer(token: String) {
        for ((index, waitMs) in LOGOUT_RETRY_DELAYS_MS.withIndex()) {
            val retry = try {
                val code = api.logout("Bearer $token").code()
                // 2xx e 4xx (exceto 429): o servidor decidiu; 5xx e 429 são passageiros como a falta de rede
                code >= 500 || code == 429
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                e.code() >= 500 || e.code() == 429
            } catch (e: IOException) {
                true
            } catch (e: Exception) {
                false
            }
            if (!retry || index == LOGOUT_RETRY_DELAYS_MS.lastIndex) return
            delay(waitMs)
        }
    }

    private suspend fun saveSession(token: TokenResponseDto) {
        store.save(Session(token.accessToken, token.user.toDomain()))
    }
}
