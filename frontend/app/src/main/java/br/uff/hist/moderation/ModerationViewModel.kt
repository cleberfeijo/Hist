package br.uff.hist.moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.model.REASON_MAX_LENGTH
import br.uff.hist.core.model.REASON_MIN_LENGTH
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.profile.ListState
import br.uff.hist.profile.PagedLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NOT_FOUND = "not_found"
private const val CONFLICT = "conflict"

fun isValidReason(reason: String): Boolean = reason.trim().length in REASON_MIN_LENGTH..REASON_MAX_LENGTH

data class ModerationQueueUiState(
    val landmarks: ListState<Landmark> = ListState(),
    val curiosities: ListState<Curiosity> = ListState(),
    /** Aviso da última decisão (aprovado, rejeitado, já revisado), exibido uma vez. */
    val notice: String? = null,
)

/**
 * Filas de pendentes. Vive na entrada da fila na pilha: o detalhe devolve a decisão pelo `savedStateHandle`
 * da fila e a tela chama [decided], que tira o item da lista sem recarregar.
 */
@HiltViewModel
class ModerationQueueViewModel @Inject constructor(repository: ModerationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ModerationQueueUiState())
    val uiState: StateFlow<ModerationQueueUiState> = _uiState.asStateFlow()

    private val landmarkLoader = PagedLoader(
        viewModelScope, repository::landmarkQueue, { it.id },
    ) { f -> _uiState.update { it.copy(landmarks = f(it.landmarks)) } }

    private val curiosityLoader = PagedLoader(
        viewModelScope, repository::curiosityQueue, { it.id },
    ) { f -> _uiState.update { it.copy(curiosities = f(it.curiosities)) } }

    // Decisões desde a última busca: o servidor encolheu a fila, então a paginação por deslocamento pularia itens.
    private var landmarkDecisions = 0
    private var curiosityDecisions = 0

    init {
        landmarkLoader.loadMore()
        curiosityLoader.loadMore()
    }

    fun loadMoreLandmarks() {
        if (landmarkDecisions > 0 && !landmarkLoader.loading) {
            landmarkLoader.reset()
            landmarkDecisions = 0
        }
        landmarkLoader.loadMore()
    }

    fun loadMoreCuriosities() {
        if (curiosityDecisions > 0 && !curiosityLoader.loading) {
            curiosityLoader.reset()
            curiosityDecisions = 0
        }
        curiosityLoader.loadMore()
    }

    fun decided(id: String, notice: String?) {
        val before = _uiState.value
        if (before.landmarks.items.any { it.id == id }) landmarkDecisions++
        if (before.curiosities.items.any { it.id == id }) curiosityDecisions++
        _uiState.update {
            it.copy(
                landmarks = it.landmarks.copy(items = it.landmarks.items.filterNot { l -> l.id == id }),
                curiosities = it.curiosities.copy(items = it.curiosities.items.filterNot { c -> c.id == id }),
                notice = notice,
            )
        }
        // Lista esvaziada com mais páginas no servidor: busca já, senão a tela ficaria sem sentinela para pedir mais.
        val now = _uiState.value
        if (now.landmarks.items.isEmpty() && now.landmarks.hasMore) loadMoreLandmarks()
        if (now.curiosities.items.isEmpty() && now.curiosities.hasMore) loadMoreCuriosities()
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }
}

enum class ModerationKind { LANDMARK, CURIOSITY }

/** Resultado que o detalhe entrega à fila: some o item e aparece o aviso. */
data class Decision(val id: String, val notice: String)

data class ModerationDetailUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** O item não existe mais (404 ao abrir): a fila se atualiza sozinha, aqui só se oferece voltar. */
    val unavailable: Boolean = false,
    val landmark: Landmark? = null,
    val curiosity: Curiosity? = null,
    val rejectDialog: Boolean = false,
    /** Fica no ViewModel: sobrevive à rotação com o diálogo aberto. */
    val reason: String = "",
    val reasonError: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    /** Preenchido quando a decisão terminou (ou o item já tinha sido revisado): a tela entrega e volta. */
    val decision: Decision? = null,
)

@HiltViewModel
class ModerationDetailViewModel @Inject constructor(
    private val repository: ModerationRepository,
    private val messages: ErrorMessages,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    private var kind: ModerationKind? = null

    private val _uiState = MutableStateFlow(ModerationDetailUiState())
    val uiState: StateFlow<ModerationDetailUiState> = _uiState.asStateFlow()

    /** Idempotente: a tela chama a cada composição inicial, a carga só roda uma vez (ou em [retry]). */
    fun open(kind: ModerationKind) {
        if (this.kind != null) return
        this.kind = kind
        load()
    }

    fun retry() {
        if (kind != null && !_uiState.value.loading) load()
    }

    private fun load() {
        val kind = kind ?: return
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            when (kind) {
                ModerationKind.LANDMARK -> when (val r = repository.landmark(id)) {
                    is ApiResult.Success -> _uiState.update { it.copy(loading = false, landmark = r.value) }
                    is ApiResult.Failure -> loadFailure(r.error)
                }
                ModerationKind.CURIOSITY -> when (val r = repository.curiosity(id)) {
                    is ApiResult.Success -> _uiState.update { it.copy(loading = false, curiosity = r.value) }
                    is ApiResult.Failure -> loadFailure(r.error)
                }
            }
        }
    }

    private fun loadFailure(error: AppError) {
        _uiState.update {
            if (error.isNotFound()) it.copy(loading = false, unavailable = true) else it.copy(loading = false, loadFailed = true)
        }
    }

    fun approve() {
        val kind = kind ?: return
        submit(R.string.moderation_approved) {
            when (kind) {
                ModerationKind.LANDMARK -> repository.approveLandmark(id)
                ModerationKind.CURIOSITY -> repository.approveCuriosity(id)
            }
        }
    }

    fun askReject() = _uiState.update { if (it.submitting) it else it.copy(rejectDialog = true, reasonError = false) }

    fun dismissReject() = _uiState.update { if (it.submitting) it else it.copy(rejectDialog = false, reasonError = false) }

    fun setReason(text: String) = _uiState.update { it.copy(reason = text, reasonError = false) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun reject(reason: String) {
        val kind = kind ?: return
        if (_uiState.value.submitting) return
        if (!isValidReason(reason)) {
            _uiState.update { it.copy(reasonError = true) }
            return
        }
        val trimmed = reason.trim()
        submit(R.string.moderation_rejected) {
            when (kind) {
                ModerationKind.LANDMARK -> repository.rejectLandmark(id, trimmed)
                ModerationKind.CURIOSITY -> repository.rejectCuriosity(id, trimmed)
            }
        }
    }

    private fun submit(successNotice: Int, call: suspend () -> ApiResult<Unit>) {
        if (_uiState.value.submitting || _uiState.value.decision != null) return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        submitting = false, rejectDialog = false,
                        decision = Decision(id, messages.string(successNotice)),
                    )
                }
                is ApiResult.Failure ->
                    if (result.error.isNotFound() || result.error.isConflict()) {
                        finishAlreadyReviewed()
                    } else {
                        _uiState.update { it.copy(submitting = false, error = messages.of(result.error)) }
                    }
            }
        }
    }

    /** Outro moderador decidiu primeiro: o item não existe mais como pendente. */
    private fun finishAlreadyReviewed() = _uiState.update {
        it.copy(
            loading = false, submitting = false, rejectDialog = false,
            decision = Decision(id, messages.string(R.string.moderation_already_reviewed)),
        )
    }

    private fun AppError.isNotFound() = this is AppError.Api && code == NOT_FOUND

    private fun AppError.isConflict() = this is AppError.Api && code == CONFLICT
}
