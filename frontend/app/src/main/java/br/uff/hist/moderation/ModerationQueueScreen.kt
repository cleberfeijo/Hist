package br.uff.hist.moderation

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.profile.ListState

internal const val SNIPPET_LENGTH = 120

@Composable
fun ModerationQueueScreen(
    onBack: () -> Unit,
    onOpenLandmark: (String) -> Unit,
    onOpenCuriosity: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModerationQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModerationQueueContent(
        state = state,
        onBack = onBack,
        onOpenLandmark = onOpenLandmark,
        onOpenCuriosity = onOpenCuriosity,
        onLoadMoreLandmarks = viewModel::loadMoreLandmarks,
        onLoadMoreCuriosities = viewModel::loadMoreCuriosities,
        onNoticeShown = viewModel::dismissNotice,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationQueueContent(
    state: ModerationQueueUiState,
    onBack: () -> Unit,
    onOpenLandmark: (String) -> Unit,
    onOpenCuriosity: (String) -> Unit,
    onLoadMoreLandmarks: () -> Unit,
    onLoadMoreCuriosities: () -> Unit,
    modifier: Modifier = Modifier,
    onNoticeShown: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            onNoticeShown()
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.contrib_tab_landmarks)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.contrib_tab_curiosities)) })
            }
            if (tab == 0) {
                QueueList(state.landmarks, { it.id }, onLoadMoreLandmarks) { LandmarkRow(it) { onOpenLandmark(it.id) } }
            } else {
                QueueList(state.curiosities, { it.id }, onLoadMoreCuriosities) { CuriosityRow(it) { onOpenCuriosity(it.id) } }
            }
        }
    }
}

@Composable
private fun <T> QueueList(
    list: ListState<T>,
    key: (T) -> String,
    onLoadMore: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        list.items.isEmpty() && !list.loaded && !list.failed -> LoadingState()
        list.items.isEmpty() && list.failed -> ErrorState(stringResource(R.string.error_unknown), onRetry = onLoadMore)
        list.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.moderation_empty), color = LocalSecondaryText.current, modifier = Modifier.padding(24.dp))
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
private fun LandmarkRow(landmark: Landmark, onClick: () -> Unit) {
    // O ponto não traz nome do autor nem data na API: só se sabe se veio do crawler (sem autor).
    val byline = if (landmark.createdBy == null) stringResource(R.string.moderation_imported) else null
    RowLayout(landmark.name, stringResource(landmark.category.label), byline, onClick)
}

@Composable
private fun CuriosityRow(curiosity: Curiosity, onClick: () -> Unit) {
    val date = DateUtils.getRelativeTimeSpanString(
        curiosity.createdAt.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    )
    val author = curiosity.authorName ?: stringResource(R.string.moderation_imported)
    RowLayout(curiosity.text.take(SNIPPET_LENGTH), curiosity.landmarkName, "$author · $date", onClick)
}

@Composable
private fun RowLayout(title: String, subtitle: String?, byline: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalSecondaryText.current)
        if (byline != null) Text(byline, style = MaterialTheme.typography.bodySmall, color = LocalSecondaryText.current)
    }
}
