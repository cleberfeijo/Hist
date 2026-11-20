package br.uff.hist.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.designsystem.components.LoadingState
import br.uff.hist.core.designsystem.components.StatusBadge
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.ModerationStatus

@Composable
fun MyContributionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyContributionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyContributionsContent(
        state = state,
        onBack = onBack,
        onLoadMoreLandmarks = viewModel::loadMoreLandmarks,
        onLoadMoreCuriosities = viewModel::loadMoreCuriosities,
        onAskDelete = viewModel::askDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        modifier = modifier,
        onErrorShown = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyContributionsContent(
    state: MyContributionsUiState,
    onBack: () -> Unit,
    onLoadMoreLandmarks: () -> Unit,
    onLoadMoreCuriosities: () -> Unit,
    onAskDelete: (DeleteTarget) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onErrorShown: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                title = { Text(stringResource(R.string.profile_my_contributions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.contrib_tab_landmarks)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.contrib_tab_curiosities)) })
            }
            if (tab == 0) {
                ContributionList(state.landmarks, { it.id }, onLoadMoreLandmarks) { landmark ->
                    ContributionRow(
                        title = landmark.name,
                        subtitle = null,
                        status = landmark.status,
                        reason = landmark.rejectionReason,
                        onDelete = if (landmark.canDelete()) ({ onAskDelete(DeleteTarget.OfLandmark(landmark.id)) }) else null,
                    )
                }
            } else {
                ContributionList(state.curiosities, { it.id }, onLoadMoreCuriosities) { curiosity ->
                    ContributionRow(
                        title = curiosity.text,
                        subtitle = curiosity.landmarkName,
                        status = curiosity.status,
                        reason = curiosity.rejectionReason,
                        onDelete = { onAskDelete(DeleteTarget.OfCuriosity(curiosity.id)) },
                    )
                }
            }
        }
    }
    if (state.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.contrib_delete_title)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, enabled = !state.deleting) { Text(stringResource(R.string.contrib_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !state.deleting) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val <T> ListState<T>.isInitialLoading get() = items.isEmpty() && !loaded && !failed

@Composable
private fun <T> ContributionList(
    list: ListState<T>,
    key: (T) -> String,
    onLoadMore: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        list.isInitialLoading -> LoadingState()
        list.items.isEmpty() && list.failed -> ErrorState(stringResource(R.string.error_unknown), onRetry = onLoadMore)
        list.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.contrib_empty),
                color = LocalSecondaryText.current,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(list.items, key = key) { item ->
                row(item)
                HorizontalDivider()
            }
            if (list.hasMore) {
                item {
                    if (list.failed) {
                        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
                    } else {
                        LaunchedEffect(list.pages) { onLoadMore() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionRow(
    title: String,
    subtitle: String?,
    status: ModerationStatus,
    reason: String?,
    onDelete: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalSecondaryText.current)
            StatusBadge(status)
            if (status == ModerationStatus.REJECTED && !reason.isNullOrBlank()) {
                Text(stringResource(R.string.contrib_reason, reason), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.contrib_delete))
            }
        }
    }
}
