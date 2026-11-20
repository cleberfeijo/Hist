package br.uff.hist.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(private val store: SessionStore) : ViewModel() {
    val session: StateFlow<Session?> = store.session

    private val _startDestination = MutableStateFlow<Any?>(null)

    /** `null` enquanto o disco não foi lido: evita piscar o Login para quem já tem sessão. */
    val startDestination: StateFlow<Any?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            store.awaitLoaded()
            _startDestination.value = currentDestination()
        }
    }

    suspend fun currentDestination(): Any =
        rootDestination(store.session.value, store.locationOnboardingDone.first())

    fun finishLocationOnboarding() {
        viewModelScope.launch { store.setLocationOnboardingDone() }
    }
}
