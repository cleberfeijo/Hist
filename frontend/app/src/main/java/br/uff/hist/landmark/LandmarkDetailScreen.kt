package br.uff.hist.landmark

import android.Manifest
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.formatDistance
import br.uff.hist.core.designsystem.formatRelativeDate
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.designsystem.components.LoadingState
import br.uff.hist.core.model.CHAT_JOIN_RADIUS_M
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.network.ErrorMessages
import coil3.compose.AsyncImage

private val PhotoHeight = 220.dp

@Composable
fun LandmarkDetailScreen(
    onBack: () -> Unit,
    onEnterChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes endMessage: Int? = null,
    onEndMessageShown: () -> Unit = {},
    viewModel: LandmarkDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messages = remember(context) { ErrorMessages(context.applicationContext) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Coleta de posição só com a tela visível; ao voltar dos ajustes, a permissão é relida.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onPermissionResult(result.values.any { it })
    }
    val snackbar = remember { SnackbarHostState() }
    EndMessageEffect(endMessage, snackbar, onEndMessageShown)
    Box(modifier) {
        LandmarkDetailContent(
            state = state,
            errorMessage = state.error?.let { messages.of(it) },
            onBack = onBack,
            onEnterChat = { state.landmark?.let { onEnterChat(it.id) } },
            onEnableLocation = {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onOpenSuggestion = viewModel::openSuggestion,
            onSuggestionText = viewModel::onSuggestionText,
            onSubmitSuggestion = viewModel::submitSuggestion,
            onDismissSuggestion = viewModel::dismissSuggestion,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Mostra o texto e só então o consome: limpar a chave antes cancelaria o `showSnackbar` em andamento. */
@Composable
internal fun EndMessageEffect(@StringRes endMessage: Int?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val context = LocalContext.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(endMessage) {
        if (endMessage != null) {
            snackbar.showSnackbar(context.getString(endMessage))
            currentOnShown()
        }
    }
}

@Composable
internal fun LandmarkDetailContent(
    state: LandmarkDetailUiState,
    errorMessage: String?,
    onBack: () -> Unit,
    onEnterChat: () -> Unit,
    onEnableLocation: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSuggestion: () -> Unit,
    onSuggestionText: (String) -> Unit,
    onSubmitSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landmark = state.landmark
        when {
            landmark != null -> DetailList(
                landmark, state, onEnterChat, onEnableLocation, onRetry, onLoadMore, onOpenSuggestion,
            )
            errorMessage != null -> ErrorState(errorMessage, onRetry)
            else -> LoadingState()
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }
    if (state.suggestion.open) {
        SuggestionDialog(state.suggestion, onSuggestionText, onSubmitSuggestion, onDismissSuggestion)
    }
}

@Composable
private fun DetailList(
    landmark: Landmark,
    state: LandmarkDetailUiState,
    onEnterChat: () -> Unit,
    onEnableLocation: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSuggestion: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") { Header(landmark, state, onEnterChat, onEnableLocation) }
        item(key = "curiosities-header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.curiosities_header, maxOf(landmark.curiosityCount, state.curiosities.size)),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSuggestion) { Text(stringResource(R.string.curiosity_suggest)) }
            }
            if (state.suggestion.sent) {
                Text(
                    stringResource(R.string.curiosity_suggest_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (state.curiositiesLoaded && state.curiosities.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.curiosities_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalSecondaryText.current,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(state.curiosities, key = { it.id }) { CuriosityRow(it) }
        if (state.curiositiesFailed) {
            item(key = "retry") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.curiosities_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSecondaryText.current,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        } else if (state.hasMore) {
            item(key = "more") {
                // Entrou na tela: pede a próxima página (o ViewModel ignora pedidos repetidos).
                LaunchedEffect(state.curiosities.size) { onLoadMore() }
                Box(Modifier.fillMaxWidth().height(48.dp))
            }
        }
        item(key = "bottom") { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Header(
    landmark: Landmark,
    state: LandmarkDetailUiState,
    onEnterChat: () -> Unit,
    onEnableLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val photoModifier = Modifier.fillMaxWidth().height(PhotoHeight)
        if (landmark.imageUrl != null) {
            AsyncImage(
                model = landmark.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = photoModifier,
                error = painterResource(landmark.category.icon),
            )
        } else {
            Image(painterResource(landmark.category.icon), contentDescription = null, modifier = photoModifier.padding(48.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            landmark.source?.let { source ->
                Text(
                    if (source.license != null) {
                        stringResource(R.string.image_credit, source.name, source.license)
                    } else {
                        stringResource(R.string.image_credit_no_license, source.name)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalSecondaryText.current,
                )
            }
            Text(landmark.name, style = MaterialTheme.typography.headlineSmall)
            Text(metaLine(landmark, state.distanceM), style = MaterialTheme.typography.bodySmall, color = LocalSecondaryText.current)
            landmark.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            ChatButton(state.chatAccess, onEnterChat, onEnableLocation)
        }
    }
}

@Composable
private fun metaLine(landmark: Landmark, distanceM: Double?): String {
    val parts = buildList {
        add(stringResource(landmark.category.label))
        val city = landmark.city
        val uf = landmark.state
        when {
            city != null && uf != null -> add(stringResource(R.string.detail_city_line, city, uf))
            city != null -> add(city)
            uf != null -> add(uf)
        }
        if (distanceM != null) add(formatDistance(distanceM))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ChatButton(access: ChatAccess, onEnterChat: () -> Unit, onEnableLocation: () -> Unit) {
    val label = when (access) {
        ChatAccess.Available -> stringResource(R.string.chat_enter)
        is ChatAccess.TooFar -> stringResource(R.string.chat_too_far, CHAT_JOIN_RADIUS_M, access.missingM)
        ChatAccess.Locating -> stringResource(R.string.chat_locating)
        ChatAccess.MockLocation -> stringResource(R.string.chat_mock_location)
        ChatAccess.NoPermission -> stringResource(R.string.chat_no_permission)
    }
    Button(onClick = onEnterChat, enabled = access == ChatAccess.Available, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
    if (access == ChatAccess.NoPermission) {
        TextButton(onClick = onEnableLocation) { Text(stringResource(R.string.location_enable)) }
    }
}

@Composable
private fun CuriosityRow(curiosity: Curiosity) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(curiosity.text, style = MaterialTheme.typography.bodyMedium)
            val attribution = curiosity.authorName
                ?: curiosity.source?.let { stringResource(R.string.curiosity_source, it.name) }
            val date = formatRelativeDate(curiosity.createdAt)
            Text(
                listOfNotNull(attribution, date).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = LocalSecondaryText.current,
            )
        }
    }
}

@Composable
private fun SuggestionDialog(
    suggestion: SuggestionState,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.curiosity_suggest_title)) },
        text = {
            OutlinedTextField(
                value = suggestion.text,
                onValueChange = onText,
                placeholder = { Text(stringResource(R.string.curiosity_suggest_hint)) },
                isError = suggestion.error != null,
                supportingText = suggestion.error?.let { { Text(it) } },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !suggestion.submitting) {
                Text(stringResource(R.string.curiosity_suggest_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !suggestion.submitting) {
                Text(stringResource(R.string.curiosity_suggest_cancel))
            }
        },
    )
}
