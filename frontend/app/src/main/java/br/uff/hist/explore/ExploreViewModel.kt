package br.uff.hist.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.core.location.EXPLORE_INTERVAL_MS
import br.uff.hist.core.location.LocationPriority
import br.uff.hist.core.location.LocationProvider
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.MAX_NEARBY_RADIUS_M
import br.uff.hist.core.model.RIO_DAS_OSTRAS
import br.uff.hist.core.model.USER_NEARBY_RADIUS_M
import br.uff.hist.core.model.USER_REFETCH_DISTANCE_M
import br.uff.hist.core.model.distanceMeters
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExploreMode { MAP, LIST }

enum class SheetTitle { NEAR_YOU, THIS_AREA }

data class ExploreUiState(
    val mode: ExploreMode = ExploreMode.MAP,
    val hasPermission: Boolean = false,
    val user: GeoPoint? = null,
    val initialCamera: GeoPoint = RIO_DAS_OSTRAS,
    val sheetTitle: SheetTitle = SheetTitle.THIS_AREA,
    val sheetItems: List<LandmarkItem> = emptyList(),
    val markers: List<LandmarkItem> = emptyList(),
    val loading: Boolean = false,
    val error: AppError? = null,
)

/**
 * Duas buscas independentes: a do usuário (sheet "Perto de você") e a da câmera (marcadores).
 * A coleta de posições só roda entre [onStart] e [onStop] (tela visível).
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    private val location: LocationProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState(hasPermission = location.hasPermission()))
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var userLandmarks: List<Landmark> = emptyList()
    private var cameraLandmarks: List<Landmark> = emptyList()
    private var user: GeoPoint? = null
    private var lastUserFetchPoint: GeoPoint? = null
    private var lastCamera: Pair<GeoPoint, Double>? = null
    private var userError: AppError? = null
    private var cameraError: AppError? = null
    private var userLoading = false
    private var cameraLoading = false
    private var visible = false

    private var locationJob: Job? = null
    private var userFetchJob: Job? = null
    private var cameraFetchJob: Job? = null

    init {
        viewModelScope.launch {
            val camera = location.lastKnown()?.point ?: RIO_DAS_OSTRAS
            _uiState.value = _uiState.value.copy(initialCamera = camera)
        }
    }

    fun onStart() {
        visible = true
        refreshPermission()
        startLocation()
    }

    fun onStop() {
        visible = false
        locationJob?.cancel()
        locationJob = null
    }

    fun setMode(mode: ExploreMode) = _uiState.update { it.copy(mode = mode) }

    fun onPermissionResult(granted: Boolean) = applyPermission(granted)

    /** Reavalia a permissão (o usuário pode tê-la concedido nos ajustes do sistema). */
    fun refreshPermission() = applyPermission(location.hasPermission())

    fun onCameraIdle(center: GeoPoint, visibleRadiusM: Double) {
        lastCamera = center to visibleRadiusM
        fetchCamera(center, visibleRadiusM)
    }

    fun retry() {
        if (userError != null) user?.let { fetchUser(it) }
        if (cameraError != null) lastCamera?.let { (c, r) -> fetchCamera(c, r) }
    }

    private fun applyPermission(granted: Boolean) {
        if (granted == _uiState.value.hasPermission) return
        if (!granted) {
            locationJob?.cancel()
            locationJob = null
            userFetchJob?.cancel()
            user = null
            lastUserFetchPoint = null
            userLandmarks = emptyList()
            userError = null
            userLoading = false
        }
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        publish()
        if (granted) startLocation()
    }

    private fun startLocation() {
        if (!visible || !_uiState.value.hasPermission || locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            location.updates(LocationPriority.BALANCED, EXPLORE_INTERVAL_MS).collect { fix ->
                user = fix.point
                val last = lastUserFetchPoint
                if (last == null || distanceMeters(last, fix.point) >= USER_REFETCH_DISTANCE_M) {
                    fetchUser(fix.point)
                } else {
                    publish()
                }
            }
        }
    }

    private fun fetchUser(point: GeoPoint) {
        lastUserFetchPoint = point
        userFetchJob?.cancel()
        userLoading = true
        userError = null
        publish()
        userFetchJob = viewModelScope.launch {
            when (val result = repository.nearby(point, USER_NEARBY_RADIUS_M)) {
                is ApiResult.Success -> userLandmarks = result.value
                is ApiResult.Failure -> userError = result.error
            }
            userLoading = false
            publish()
        }
    }

    private fun fetchCamera(center: GeoPoint, visibleRadiusM: Double) {
        cameraFetchJob?.cancel()
        cameraLoading = true
        cameraError = null
        publish()
        val radius = min(ceil(visibleRadiusM).toInt(), MAX_NEARBY_RADIUS_M)
        cameraFetchJob = viewModelScope.launch {
            when (val result = repository.nearby(center, radius)) {
                is ApiResult.Success -> cameraLandmarks = result.value
                is ApiResult.Failure -> cameraError = result.error
            }
            cameraLoading = false
            publish()
        }
    }

    private fun publish() {
        val hasPermission = _uiState.value.hasPermission
        val markers = toItems(cameraLandmarks, user)
        _uiState.update {
            it.copy(
                user = user,
                markers = markers,
                sheetTitle = if (hasPermission) SheetTitle.NEAR_YOU else SheetTitle.THIS_AREA,
                sheetItems = if (hasPermission) toItems(userLandmarks, user) else toItems(cameraLandmarks, null),
                loading = userLoading || cameraLoading,
                error = userError ?: cameraError,
            )
        }
    }
}

private inline fun MutableStateFlow<ExploreUiState>.update(f: (ExploreUiState) -> ExploreUiState) {
    value = f(value)
}
