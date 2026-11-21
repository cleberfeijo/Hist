package br.uff.hist.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.location.LocationPriority
import br.uff.hist.core.location.LocationProvider
import br.uff.hist.core.model.Category
import br.uff.hist.core.model.CURIOSITY_MAX_LENGTH
import br.uff.hist.core.model.CURIOSITY_MIN_LENGTH
import br.uff.hist.core.model.DESCRIPTION_MAX_LENGTH
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.LANDMARK_NAME_MAX_LENGTH
import br.uff.hist.core.model.LANDMARK_NAME_MIN_LENGTH
import br.uff.hist.core.model.RIO_DAS_OSTRAS
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.NewLandmarkRequest
import br.uff.hist.explore.LandmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/** Chaves de campo renderizadas pela tela (as mesmas do servidor); `latitude`/`longitude` caem no erro geral. */
object SuggestFields {
    const val NAME = "name"
    const val CATEGORY = "category"
    const val DESCRIPTION = "description"
    const val CURIOSITY = "curiosity"
    val ALL = setOf(NAME, CATEGORY, DESCRIPTION, CURIOSITY)
}

data class SuggestLandmarkUiState(
    /** `null` enquanto a posição inicial é resolvida; depois acompanha o centro do mapa. */
    val pin: GeoPoint? = null,
    val name: String = "",
    val category: Category? = null,
    val description: String = "",
    val curiosity: String = "",
    val submitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val done: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SuggestLandmarkViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    private val location: LocationProvider,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuggestLandmarkUiState())
    val uiState: StateFlow<SuggestLandmarkUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Sem permissão pula direto para a última posição conhecida/fallback.
            val fix = (if (location.hasPermission()) location.current(LocationPriority.BALANCED) else null)
                ?: location.lastKnown()
            val initial = fix?.point ?: RIO_DAS_OSTRAS
            _uiState.update { if (it.pin == null) it.copy(pin = initial) else it }
        }
    }

    fun onCameraMoved(center: GeoPoint) = _uiState.update { it.copy(pin = center) }
    fun onNameChange(v: String) = edit(SuggestFields.NAME) { it.copy(name = v) }
    fun onCategoryChange(v: Category) = edit(SuggestFields.CATEGORY) { it.copy(category = v) }
    fun onDescriptionChange(v: String) = edit(SuggestFields.DESCRIPTION) { it.copy(description = v) }
    fun onCuriosityChange(v: String) = edit(SuggestFields.CURIOSITY) { it.copy(curiosity = v) }

    private fun edit(field: String, change: (SuggestLandmarkUiState) -> SuggestLandmarkUiState) =
        _uiState.update { change(it).copy(fieldErrors = it.fieldErrors - field, error = null) }

    fun submit() {
        val current = _uiState.value
        val pin = current.pin
        if (current.submitting || current.done || pin == null) return
        val local = validate(current)
        if (local.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = local, error = null) }
            return
        }
        val category = checkNotNull(current.category)
        _uiState.update { it.copy(submitting = true, fieldErrors = emptyMap(), error = null) }
        viewModelScope.launch {
            val request = NewLandmarkRequest(
                name = current.name.trim(),
                latitude = pin.latitude,
                longitude = pin.longitude,
                description = current.description.trim().ifEmpty { null },
                category = category.id,
                curiosity = current.curiosity.trim().ifEmpty { null },
            )
            when (val result = repository.suggestLandmark(request)) {
                is ApiResult.Success -> _uiState.update { it.copy(submitting = false, done = true) }
                is ApiResult.Failure -> _uiState.update { it.withFailure(result.error) }
            }
        }
    }

    private fun validate(s: SuggestLandmarkUiState): Map<String, String> = buildMap {
        if (s.name.trim().length !in LANDMARK_NAME_MIN_LENGTH..LANDMARK_NAME_MAX_LENGTH) put(SuggestFields.NAME, messages.string(R.string.suggest_error_name))
        if (s.category == null) put(SuggestFields.CATEGORY, messages.string(R.string.suggest_error_category))
        if (s.description.trim().length > DESCRIPTION_MAX_LENGTH) {
            put(SuggestFields.DESCRIPTION, messages.string(R.string.suggest_error_description))
        }
        val curiosity = s.curiosity.trim()
        if (curiosity.isNotEmpty() && curiosity.length !in CURIOSITY_MIN_LENGTH..CURIOSITY_MAX_LENGTH) {
            put(SuggestFields.CURIOSITY, messages.string(R.string.suggest_error_curiosity))
        }
    }

    private fun SuggestLandmarkUiState.withFailure(error: AppError): SuggestLandmarkUiState {
        if (error is AppError.Api && error.fieldErrors.isNotEmpty()) {
            val fields = error.fieldErrors.mapValues { it.value.take(ErrorMessages.MAX_SERVER_MESSAGE) }
            val unrendered = fields.filterKeys { it !in SuggestFields.ALL }
            return copy(
                submitting = false,
                fieldErrors = fields.filterKeys { it in SuggestFields.ALL },
                error = if (unrendered.isEmpty()) null else generalMessage(error, unrendered),
            )
        }
        return copy(submitting = false, error = messages.of(error))
    }

    private fun generalMessage(error: AppError.Api, unrendered: Map<String, String>): String =
        unrendered.values.filter { it.isNotBlank() }.joinToString("\n").take(ErrorMessages.MAX_SERVER_MESSAGE)
            .ifBlank { messages.of(error) }
            .ifBlank { messages.string(R.string.error_unknown) }
}
