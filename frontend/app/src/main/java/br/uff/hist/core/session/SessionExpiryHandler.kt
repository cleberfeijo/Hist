package br.uff.hist.core.session

import javax.inject.Inject
import javax.inject.Singleton
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
        scope.launch { events.expired.collect { store.clear() } }
    }
}
