package br.uff.hist.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.components.ErrorState
import br.uff.hist.core.designsystem.components.LoadingState
import kotlin.math.roundToInt

/** Quantos code points antes do limite o contador aparece. */
private const val COUNTER_THRESHOLD = 100

/**
 * [onClose] é chamado quando a conversa termina (com o texto de encerramento, se houver) ou o usuário volta;
 * quem navega decide como mostrar a mensagem.
 */
@Composable
fun ChatScreen(
    onClose: (endMessage: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val leaveAndClose: () -> Unit = {
        viewModel.leave()
        currentOnClose(null)
    }
    BackHandler(onBack = leaveAndClose)

    when (val current = ui) {
        ChatScreenState.Loading -> LoadingState(modifier)
        is ChatScreenState.Failed -> ErrorState(current.message, onRetry = viewModel::retry, modifier = modifier)
        is ChatScreenState.Ready -> {
            val phase = current.chat.phase
            LaunchedEffect(phase) {
                if (phase is ChatPhase.Ended) currentOnClose(endMessage(phase.reason))
            }
            ChatContent(
                state = current.chat,
                onSend = viewModel::send,
                onBack = leaveAndClose,
                onNoticeShown = viewModel::consumeNotice,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun ChatContent(
    state: ChatState,
    onSend: (String) -> SendResult,
    onBack: () -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice
    val currentOnNoticeShown by rememberUpdatedState(onNoticeShown)
    // Mostra e só então consome: limpar o aviso antes cancelaria o `showSnackbar` em andamento.
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            currentOnNoticeShown()
        }
    }
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ChatTopBar(state, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Composer(
                maxLength = state.maxMessageLength,
                enabled = state.phase == ChatPhase.Active,
                onSend = onSend,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.chat_ephemeral_notice),
                style = MaterialTheme.typography.labelMedium,
                color = LocalSecondaryText.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.phase == ChatPhase.Reconnecting) {
                Banner(stringResource(R.string.chat_reconnecting))
            }
            if (state.warnLeaving && state.distanceM != null) {
                Banner(
                    stringResource(
                        R.string.chat_leaving_warning,
                        state.distanceM.roundToInt(),
                        state.leaveRadiusM,
                    ),
                )
            }
            MessageList(state.items, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatTopBar(state: ChatState, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.landmarkName.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                val members = state.memberCount
                if (members != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text(
                            text = pluralStringResource(R.plurals.chat_member_count, members, members),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalSecondaryText.current,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MessageList(items: List<ChatItem>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val lastKey = items.lastOrNull()?.key
    LaunchedEffect(lastKey) {
        if (items.isEmpty()) return@LaunchedEffect
        // layoutInfo ainda reflete a lista anterior: "perto do fim" = o último item antigo estava (quase) visível.
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val nearBottom = info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 2
        val mine = (items.last() as? ChatItem.Msg)?.mine == true
        if (nearBottom || mine) listState.scrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.Msg -> Bubble(item)
                is ChatItem.PresenceNote -> PresenceLine(item)
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatItem.Msg) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (msg.mine) colors.primary else colors.surface,
            contentColor = if (msg.mine) colors.onPrimary else colors.onSurface,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!msg.mine) {
                    Text(msg.author, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
                Text(msg.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PresenceLine(note: ChatItem.PresenceNote) {
    Text(
        text = stringResource(if (note.joined) R.string.chat_presence_joined else R.string.chat_presence_left, note.name),
        style = MaterialTheme.typography.labelMedium,
        color = LocalSecondaryText.current,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Composer(maxLength: Int, enabled: Boolean, onSend: (String) -> SendResult) {
    var text by rememberSaveable { mutableStateOf("") }
    val length = text.codePointCount(0, text.length)
    val tooLong = length > maxLength
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                maxLines = 4,
                isError = tooLong,
                supportingText = if (length >= maxLength - COUNTER_THRESHOLD) {
                    { Text(stringResource(R.string.chat_counter, length, maxLength)) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { if (onSend(text) == SendResult.SENT) text = "" },
                enabled = enabled && text.isNotBlank() && !tooLong,
            ) { Text(stringResource(R.string.chat_send)) }
        }
    }
}
