package br.uff.hist.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.ModerationStatus
import br.uff.hist.core.model.Page
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ponto próprio já aprovado é público e não pode ser excluído pelo autor; os demais podem. */
fun Landmark.canDelete(): Boolean = status != ModerationStatus.APPROVED

data class ListState<T>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false,
    /** A última página falhou: os itens já carregados ficam e a tela oferece "Tentar de novo". */
    val failed: Boolean = false,
    /** Evita mostrar o estado vazio enquanto a primeira página ainda carrega. */
    val loaded: Boolean = false,
)

sealed interface DeleteTarget {
    val id: String

    data class OfLandmark(override val id: String) : DeleteTarget
    data class OfCuriosity(override val id: String) : DeleteTarget
}

data class MyContributionsUiState(
    val landmarks: ListState<Landmark> = ListState(),
    val curiosities: ListState<Curiosity> = ListState(),
    val deleteTarget: DeleteTarget? = null,
    val deleting: Boolean = false,
    val error: String? = null,
)

/** Carrega uma lista paginada (uma página por vez, sem duplicar itens). */
private class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (Int) -> ApiResult<Page<T>>,
    private val id: (T) -> String,
    private val update: ((ListState<T>) -> ListState<T>) -> Unit,
) {
    private var nextPage = 1
    private var more = true
    private var job: Job? = null

    fun loadMore() {
        if (!more || job?.isActive == true) return
        update { it.copy(failed = false) }
        job = scope.launch {
            when (val result = fetch(nextPage)) {
                is ApiResult.Success -> {
                    nextPage = result.value.page + 1
                    more = result.value.hasMore
                    update {
                        it.copy(
                            items = (it.items + result.value.items).distinctBy(id),
                            hasMore = more,
                            loaded = true,
                        )
                    }
                }
                is ApiResult.Failure -> update { it.copy(failed = true) }
            }
        }
    }
}

@HiltViewModel
class MyContributionsViewModel @Inject constructor(
    private val repository: AccountRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyContributionsUiState())
    val uiState: StateFlow<MyContributionsUiState> = _uiState.asStateFlow()

    private val landmarkLoader = PagedLoader(
        viewModelScope, repository::myLandmarks, { it.id },
    ) { f -> _uiState.update { it.copy(landmarks = f(it.landmarks)) } }

    private val curiosityLoader = PagedLoader(
        viewModelScope, repository::myCuriosities, { it.id },
    ) { f -> _uiState.update { it.copy(curiosities = f(it.curiosities)) } }

    init {
        landmarkLoader.loadMore()
        curiosityLoader.loadMore()
    }

    fun loadMoreLandmarks() = landmarkLoader.loadMore()

    fun loadMoreCuriosities() = curiosityLoader.loadMore()

    fun askDelete(target: DeleteTarget) {
        val allowed = when (target) {
            is DeleteTarget.OfCuriosity -> true
            is DeleteTarget.OfLandmark ->
                _uiState.value.landmarks.items.firstOrNull { it.id == target.id }?.canDelete() == true
        }
        if (allowed && !_uiState.value.deleting) _uiState.update { it.copy(deleteTarget = target) }
    }

    fun cancelDelete() = _uiState.update { if (it.deleting) it else it.copy(deleteTarget = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun confirmDelete() {
        val state = _uiState.value
        val target = state.deleteTarget ?: return
        if (state.deleting) return
        _uiState.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val result = when (target) {
                is DeleteTarget.OfLandmark -> repository.deleteLandmark(target.id)
                is DeleteTarget.OfCuriosity -> repository.deleteCuriosity(target.id)
            }
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> when (target) {
                        is DeleteTarget.OfLandmark -> it.copy(
                            landmarks = it.landmarks.copy(items = it.landmarks.items.filterNot { l -> l.id == target.id }),
                        )
                        is DeleteTarget.OfCuriosity -> it.copy(
                            curiosities = it.curiosities.copy(items = it.curiosities.items.filterNot { c -> c.id == target.id }),
                        )
                    }.copy(deleting = false, deleteTarget = null)
                    is ApiResult.Failure -> it.copy(deleting = false, deleteTarget = null, error = messages.of(result.error))
                }
            }
        }
    }
}
