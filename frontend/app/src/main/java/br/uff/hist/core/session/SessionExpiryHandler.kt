package br.uff.hist.core.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Limpa a sessão sempre que alguma chamada reporta token inválido/revogado. */
@Singleton
class SessionExpiryHandler @Inject constructor(
    private val events: SessionEvents,
    private val store: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            events.expired.collect {
                try {
                    store.clear()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Uma falha ao limpar não pode derrubar o coletor: o próximo evento tenta de novo.
                }
            }
        }
    }
}
