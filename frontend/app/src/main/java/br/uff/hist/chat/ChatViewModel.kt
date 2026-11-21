package br.uff.hist.chat

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.location.LocationProvider
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.core.session.SessionStore
import br.uff.hist.explore.LandmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cria a [ChatSession] com as dependências do app; existe para os testes do ViewModel poderem usar fakes. */
class ChatSessionFactory @Inject constructor(
    private val sockets: ChatSocketFactory,
    private val location: LocationProvider,
    private val store: SessionStore,
    private val sessionEvents: SessionEvents,
) {
    fun create(landmarkId: String, landmarkPoint: GeoPoint, scope: CoroutineScope) =
        ChatSession(landmarkId, landmarkPoint, sockets, location, store, sessionEvents, scope)
}

sealed interface ChatScreenState {
    data object Loading : ChatScreenState
    data class Failed(val message: String) : ChatScreenState
    data class Ready(val chat: ChatState) : ChatScreenState
}

/** Texto de encerramento mostrado no detalhe; `null` quando não há nada a dizer. */
@StringRes
fun endMessage(reason: EndReason): Int? = when (reason) {
    EndReason.OUT_OF_RANGE -> R.string.chat_out_of_range
    EndReason.BACKGROUND_TIMEOUT -> R.string.chat_background_timeout
    EndReason.MOCK_LOCATION -> R.string.chat_mock_location
    EndReason.NOT_FOUND -> R.string.chat_not_found
    EndReason.NO_LOCATION_PERMISSION -> R.string.chat_no_permission
    EndReason.NO_PRECISE_LOCATION -> R.string.chat_needs_precise
    EndReason.TOO_FAR_TO_JOIN -> R.string.chat_too_far_to_join
    // SESSION_EXPIRED é tratado pela navegação raiz (volta ao Login); LEFT foi decisão do usuário.
    EndReason.SESSION_EXPIRED, EndReason.LEFT -> null
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LandmarkRepository,
    private val sessions: ChatSessionFactory,
    private val foreground: AppForegroundSignal,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val landmarkId: String = checkNotNull(savedStateHandle["landmarkId"])

    private val _uiState = MutableStateFlow<ChatScreenState>(ChatScreenState.Loading)
    val uiState: StateFlow<ChatScreenState> = _uiState.asStateFlow()

    private var session: ChatSession? = null

    init {
        load()
    }

    fun retry() {
        if (session == null && _uiState.value is ChatScreenState.Failed) load()
    }

    private fun load() {
        _uiState.value = ChatScreenState.Loading
        viewModelScope.launch {
            when (val result = repository.get(landmarkId)) {
                is ApiResult.Failure -> _uiState.value = ChatScreenState.Failed(messages.of(result.error))
                is ApiResult.Success -> begin(result.value.name, result.value.location)
            }
        }
    }

    private fun CoroutineScope.begin(landmarkName: String, point: GeoPoint) {
        val created = sessions.create(landmarkId, point, viewModelScope)
        session = created
        created.start()
        launch { created.state.collect {
                // O nome já carregado evita o título vazio até o `welcome`, que pode sobrescrevê-lo.
                _uiState.value = ChatScreenState.Ready(it.copy(landmarkName = it.landmarkName ?: landmarkName))
            } }
        launch {
            // O sinal pode reemitir o estado atual ao assinar; só reage a transições reais.
            var inBackground = false
            foreground.events().collect { visible ->
                if (visible && inBackground) created.onAppForeground()
                if (!visible && !inBackground) created.onAppBackground()
                inBackground = !visible
            }
        }
    }

    fun send(text: String): SendResult = session?.send(text) ?: SendResult.NOT_ACTIVE

    fun consumeNotice() {
        session?.consumeNotice()
    }

    fun leave() {
        session?.leave()
    }

    override fun onCleared() {
        session?.leave()
    }
}
