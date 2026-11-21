package br.uff.hist.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.location.rememberLocationPermissionActions
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.network.ErrorMessages

private val SheetPeekHeight = 168.dp
private val SheetMaxHeight = 520.dp
private val SheetCorner = 22.dp

@Composable
fun ExploreScreen(
    onOpenLandmark: (String) -> Unit,
    onSuggestLandmark: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messages = remember(context) { ErrorMessages(context.applicationContext) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Coleta de posição só com a tela visível; ao voltar dos ajustes, a permissão é relida.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    val permissions = rememberLocationPermissionActions(onResult = viewModel::refreshPermission)
    ExploreContent(
        state = state,
        errorMessage = state.error?.let { messages.of(it) },
        onSetMode = viewModel::setMode,
        onCameraIdle = viewModel::onCameraIdle,
        onRetry = viewModel::retry,
        onEnableLocation = permissions.requestLocation,
        onEnablePrecise = permissions.requestPrecise,
        onCenteredOnUser = viewModel::onCenteredOnUser,
        onOpenLandmark = onOpenLandmark,
        onSuggestLandmark = onSuggestLandmark,
        modifier = modifier,
    )
    permissions.Dialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreContent(
    state: ExploreUiState,
    errorMessage: String?,
    onSetMode: (ExploreMode) -> Unit,
    onCameraIdle: (GeoPoint, Double) -> Unit,
    onRetry: () -> Unit,
    onEnableLocation: () -> Unit,
    onEnablePrecise: () -> Unit,
    onCenteredOnUser: () -> Unit,
    onOpenLandmark: (String) -> Unit,
    onSuggestLandmark: () -> Unit,
    modifier: Modifier = Modifier,
    // Ponto de injeção: o GoogleMap não roda em testes locais (Robolectric).
    mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
        ExploreMap(
            state = state,
            onCameraIdle = onCameraIdle,
            onMarkerClick = onOpenLandmark,
            onCenteredOnUser = onCenteredOnUser,
            contentPadding = PaddingValues(bottom = SheetPeekHeight),
            modifier = mapModifier,
        )
    },
) {
    val mapMode = state.mode == ExploreMode.MAP
    val topBar: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeSelector(state.mode, onSetMode)
            if (!state.hasPermission) {
                PermissionBanner(stringResource(R.string.location_banner), R.string.location_enable, onEnableLocation)
            } else if (!state.hasPreciseLocation) {
                PermissionBanner(stringResource(R.string.location_approximate_banner), R.string.location_enable_precise, onEnablePrecise)
            }
            if (errorMessage != null && (mapMode || state.sheetItems.isNotEmpty())) ErrorBanner(errorMessage, onRetry)
        }
    }
    val listContent: @Composable (Modifier) -> Unit = { listModifier ->
        Column(listModifier) {
            Text(
                stringResource(if (state.sheetTitle == SheetTitle.NEAR_YOU) R.string.sheet_near_you else R.string.sheet_this_area),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when {
                !mapMode && errorMessage != null && state.sheetItems.isEmpty() ->
                    Box(Modifier.fillMaxWidth().height(220.dp)) { ErrorState(errorMessage, onRetry) }
                state.sheetItems.isNotEmpty() -> LandmarkList(state.sheetItems, onOpenLandmark, Modifier.fillMaxWidth())
                state.sheetLoaded -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (state.sheetTitle == SheetTitle.NEAR_YOU) R.string.sheet_empty_near_you else R.string.sheet_empty_this_area),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalSecondaryText.current,
                    )
                }
                errorMessage == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState()
    // Com o sheet expandido o botão cobriria as linhas da lista.
    val sheetExpanded = mapMode && scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    Box(modifier.fillMaxSize()) {
        if (mapMode) {
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = SheetPeekHeight,
                sheetShape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetContent = { listContent(Modifier.fillMaxWidth().heightIn(max = SheetMaxHeight)) },
            ) { _ ->
                Box(Modifier.fillMaxSize()) {
                    mapContent(Modifier.fillMaxSize())
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    ) { topBar() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                topBar()
                listContent(Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (!sheetExpanded) {
            FloatingActionButton(
                onClick = onSuggestLandmark,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (mapMode) SheetPeekHeight + 16.dp else 16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.suggest_landmark)) }
        }
    }
}

@Composable
private fun ModeSelector(mode: ExploreMode, onSetMode: (ExploreMode) -> Unit) {
    val modes = listOf(ExploreMode.MAP to R.string.explore_mode_map, ExploreMode.LIST to R.string.explore_mode_list)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onSetMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun PermissionBanner(message: String, @StringRes actionLabel: Int, onEnable: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = LocalSecondaryText.current,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = onEnable) { Text(stringResource(actionLabel)) }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
