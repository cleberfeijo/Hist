package br.uff.hist.landmark

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.location.EXPLORE_INTERVAL_MS
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.location.LocationPriority
import br.uff.hist.core.location.LocationProvider
import br.uff.hist.core.model.CHAT_JOIN_RADIUS_M
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.distanceMeters
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.explore.LandmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MIN_SUGGESTION_LENGTH = 10
private const val MAX_SUGGESTION_LENGTH = 2000

sealed interface ChatAccess {
    data object Available : ChatAccess
    data class TooFar(val missingM: Int) : ChatAccess
    data object NoPermission : ChatAccess
    data object Locating : ChatAccess
    data object MockLocation : ChatAccess
}

data class SuggestionState(
    val open: Boolean = false,
    val text: String = "",
    val submitting: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
)

data class LandmarkDetailUiState(
    val landmark: Landmark? = null,
    val distanceM: Double? = null,
    val chatAccess: ChatAccess = ChatAccess.Locating,
    val curiosities: List<Curiosity> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val error: AppError? = null,
    val suggestion: SuggestionState = SuggestionState(),
    /** A última página de curiosidades falhou: os itens já carregados ficam e a tela mostra "Tentar de novo". */
    val curiositiesFailed: Boolean = false,
    /** Evita mostrar o estado vazio enquanto a primeira página ainda carrega. */
    val curiositiesLoaded: Boolean = true,
)

/** A coleta de posições só roda entre [onStart] e [onStop] (tela visível). */
@HiltViewModel
class LandmarkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LandmarkRepository,
    private val location: LocationProvider,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["id"])

    private val _uiState = MutableStateFlow(
        LandmarkDetailUiState(
            chatAccess = if (location.hasPermission()) ChatAccess.Locating else ChatAccess.NoPermission,
            curiositiesLoaded = false,
        ),
    )
    val uiState: StateFlow<LandmarkDetailUiState> = _uiState.asStateFlow()

    private var hasPermission = location.hasPermission()
    private var fix: LocationFix? = null
    private var nextPage = 1
    private var more = true
    private var curiositiesJob: Job? = null
    private var landmarkJob: Job? = null
    private var locationJob: Job? = null
    private var visible = false

    init {
        loadLandmark()
        loadCuriosities()
    }

    fun onStart() {
        visible = true
        applyPermission(location.hasPermission())
        startLocation()
    }

    fun onStop() {
        visible = false
        locationJob?.cancel()
        locationJob = null
    }

    fun onPermissionResult(granted: Boolean) = applyPermission(granted)

    fun loadMore() {
        if (!more || curiositiesJob?.isActive == true) return
        loadCuriosities()
    }

    fun retry() {
        if (_uiState.value.landmark == null) loadLandmark()
        if (_uiState.value.curiositiesFailed) loadCuriosities()
    }

    fun openSuggestion() = _uiState.update {
        it.copy(suggestion = SuggestionState(open = true, text = it.suggestion.text))
    }

    fun onSuggestionText(t: String) = _uiState.update {
        it.copy(suggestion = it.suggestion.copy(text = t, error = null))
    }

    fun dismissSuggestion() = _uiState.update {
        if (it.suggestion.submitting) it else it.copy(suggestion = it.suggestion.copy(open = false, error = null))
    }

    fun submitSuggestion() {
        val current = _uiState.value.suggestion
        if (current.submitting) return
        val text = current.text.trim()
        if (text.length !in MIN_SUGGESTION_LENGTH..MAX_SUGGESTION_LENGTH) {
            setSuggestion { it.copy(error = messages.string(R.string.curiosity_suggest_invalid)) }
            return
        }
        setSuggestion { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.suggestCuriosity(id, text)) {
                is ApiResult.Success -> setSuggestion { SuggestionState(sent = true) }
                is ApiResult.Failure -> setSuggestion { it.copy(submitting = false, error = messages.of(result.error)) }
            }
        }
    }

    private fun setSuggestion(f: (SuggestionState) -> SuggestionState) =
        _uiState.update { it.copy(suggestion = f(it.suggestion)) }

    private fun applyPermission(granted: Boolean) {
        if (granted == hasPermission) return
        hasPermission = granted
        if (!granted) {
            locationJob?.cancel()
            locationJob = null
            fix = null
        }
        publishAccess()
        if (granted) startLocation()
    }

    private fun startLocation() {
        if (!visible || !hasPermission || locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            location.updates(LocationPriority.BALANCED, EXPLORE_INTERVAL_MS).collect {
                fix = it
                publishAccess()
            }
        }
    }

    private fun loadLandmark() {
        if (landmarkJob?.isActive == true) return
        _uiState.update { it.copy(loading = true, error = null) }
        landmarkJob = viewModelScope.launch {
            when (val result = repository.get(id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(landmark = result.value, loading = false) }
                    publishAccess()
                }
                is ApiResult.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    private fun loadCuriosities() {
        _uiState.update { it.copy(curiositiesFailed = false) }
        curiositiesJob = viewModelScope.launch {
            when (val result = repository.curiosities(id, nextPage)) {
                is ApiResult.Success -> {
                    nextPage = result.value.page + 1
                    more = result.value.hasMore
                    _uiState.update {
                        it.copy(curiosities = it.curiosities + result.value.items, hasMore = more, curiositiesLoaded = true)
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(curiositiesFailed = true) }
            }
        }
    }

    private fun publishAccess() {
        val target = _uiState.value.landmark
        val current = fix
        val distance = if (target != null && current != null) distanceMeters(current.point, target.location) else null
        val access = when {
            !hasPermission -> ChatAccess.NoPermission
            current == null || distance == null -> ChatAccess.Locating
            current.isMock -> ChatAccess.MockLocation
            distance <= CHAT_JOIN_RADIUS_M -> ChatAccess.Available
            else -> ChatAccess.TooFar(ceil(distance - CHAT_JOIN_RADIUS_M).toInt())
        }
        _uiState.update { it.copy(distanceM = distance, chatAccess = access) }
    }
}
