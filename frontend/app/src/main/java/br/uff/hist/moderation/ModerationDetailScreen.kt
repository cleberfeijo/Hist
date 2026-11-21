package br.uff.hist.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.designsystem.components.LoadingState
import br.uff.hist.core.model.GeoPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

private const val MINI_MAP_ZOOM = 16f
private val MINI_MAP_HEIGHT = 180.dp

@Composable
fun ModerationDetailScreen(
    kind: ModerationKind,
    onBack: () -> Unit,
    onDecided: (Decision) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModerationDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind) { viewModel.open(kind) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.decision) { state.decision?.let(onDecided) }
    ModerationDetailContent(
        state = state,
        onBack = onBack,
        onApprove = viewModel::approve,
        onAskReject = viewModel::askReject,
        onReasonChange = viewModel::setReason,
        onConfirmReject = { viewModel.reject(state.reason) },
        onDismissReject = viewModel::dismissReject,
        onRetry = viewModel::retry,
        onErrorShown = viewModel::dismissError,
        mapContent = { MiniMap(it, Modifier.fillMaxWidth().height(MINI_MAP_HEIGHT)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationDetailContent(
    state: ModerationDetailUiState,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    onAskReject: () -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirmReject: () -> Unit,
    onDismissReject: () -> Unit,
    onRetry: () -> Unit,
    mapContent: @Composable (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    onErrorShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val landmark = state.landmark
        val curiosity = state.curiosity
        when {
            state.unavailable -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.moderation_unavailable), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            state.loadFailed -> ErrorState(stringResource(R.string.error_unknown), onRetry = onRetry, modifier = Modifier.padding(padding))
            landmark == null && curiosity == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (landmark != null) {
                    Text(landmark.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(landmark.category.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalSecondaryText.current,
                    )
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) { mapContent(landmark.location) }
                    landmark.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                if (curiosity != null) {
                    curiosity.landmarkName?.let {
                        Text(
                            stringResource(R.string.moderation_landmark_of, it),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalSecondaryText.current,
                        )
                    }
                    Text(curiosity.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        curiosity.authorName ?: stringResource(R.string.moderation_imported),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSecondaryText.current,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onAskReject, enabled = !state.submitting, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.moderation_reject))
                    }
                    Button(onClick = onApprove, enabled = !state.submitting, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.moderation_approve))
                    }
                }
            }
        }
    }
    if (state.rejectDialog) {
        AlertDialog(
            onDismissRequest = onDismissReject,
            title = { Text(stringResource(R.string.moderation_reject_title)) },
            text = {
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(R.string.moderation_reject_field)) },
                    isError = state.reasonError,
                    supportingText = if (state.reasonError) ({ Text(stringResource(R.string.moderation_reason_error)) }) else null,
                    enabled = !state.submitting,
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmReject, enabled = !state.submitting) { Text(stringResource(R.string.moderation_reject)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissReject, enabled = !state.submitting) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Mapa estático: sem gestos, sem controles, só o marcador no ponto. */
@Composable
private fun MiniMap(location: GeoPoint, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = remember(dark) {
        MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)
    }
    val position = LatLng(location.latitude, location.longitude)
    val camera = rememberCameraPositionState { this.position = CameraPosition.fromLatLngZoom(position, MINI_MAP_ZOOM) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapStyleOptions = style),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = false,
            scrollGesturesEnabledDuringRotateOrZoom = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = false,
        ),
    ) {
        Marker(state = rememberUpdatedMarkerState(position = position))
    }
}
