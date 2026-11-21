package br.uff.hist.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import br.uff.hist.auth.LoginScreen
import br.uff.hist.auth.RegisterScreen
import br.uff.hist.chat.ChatScreen
import br.uff.hist.contribute.SuggestLandmarkScreen
import br.uff.hist.core.designsystem.components.LoadingState
import br.uff.hist.landmark.LandmarkDetailScreen
import br.uff.hist.onboarding.LocationOnboardingScreen
import br.uff.hist.moderation.Decision
import br.uff.hist.moderation.ModerationDetailScreen
import br.uff.hist.moderation.ModerationKind
import br.uff.hist.moderation.ModerationQueueScreen
import br.uff.hist.moderation.ModerationQueueViewModel
import br.uff.hist.profile.MyContributionsScreen
import br.uff.hist.profile.SettingsScreen
import kotlinx.coroutines.launch

/** Chave no `savedStateHandle` do detalhe com o texto (string res) de encerramento do chat; consumido uma vez. */
private const val CHAT_END_MESSAGE_KEY = "chat_end_message"

/** Chaves no `savedStateHandle` da fila de moderação com a decisão do detalhe; consumidas uma vez. */
private const val DECISION_ID_KEY = "moderation_decision_id"
private const val DECISION_NOTICE_KEY = "moderation_decision_notice"

@Composable
fun HistNavHost(modifier: Modifier = Modifier, viewModel: RootViewModel = hiltViewModel()) {
    val start by viewModel.startDestination.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val startDestination = start
    if (startDestination == null) {
        LoadingState(modifier)
        return
    }

    val navController = rememberNavController()
    val isModerator = session?.user?.isModerator
    val scope = rememberCoroutineScope()

    // Sessão perdida (logout, revogada, expirada) fora das telas de entrada: volta ao Login e zera a pilha.
    LaunchedEffect(session) {
        if (session == null) {
            val destination = navController.currentBackStackEntry?.destination
            val onEntryScreen = destination == null || destination.hasRoute<Login>() || destination.hasRoute<Register>()
            if (!onEntryScreen) navController.navigateClearingStack(Login)
        }
    }

    val enterApp: () -> Unit = {
        scope.launch { navController.navigateClearingStack(viewModel.currentDestination()) }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<Login> {
            LoginScreen(onLoggedIn = enterApp, onRegister = { navController.navigate(Register) })
        }
        composable<Register> {
            RegisterScreen(onRegistered = enterApp, onBackToLogin = { navController.popBackStack() })
        }
        composable<LocationOnboarding> {
            LocationOnboardingScreen(onDone = {
                viewModel.finishLocationOnboarding()
                navController.navigateClearingStack(Main)
            })
        }
        composable<Main> {
            MainScaffold(
                onOpenLandmark = { navController.navigate(LandmarkDetail(it)) },
                onSuggestLandmark = { navController.navigate(SuggestLandmark) },
                user = session?.user,
                onMyContributions = { navController.navigate(MyContributions) },
                onModeration = { navController.navigate(ModerationQueue) },
                onSettings = { navController.navigate(Settings) },
            )
        }
        composable<LandmarkDetail> { entry ->
            val handle = entry.savedStateHandle
            val endMessage by handle.getStateFlow<Int?>(CHAT_END_MESSAGE_KEY, null).collectAsStateWithLifecycle()
            LandmarkDetailScreen(
                onBack = { navController.popBackStack() },
                onEnterChat = { navController.navigate(Chat(it)) },
                endMessage = endMessage,
                onEndMessageShown = { handle[CHAT_END_MESSAGE_KEY] = null },
            )
        }
        composable<Chat> {
            ChatScreen(onClose = { message ->
                // Sair e o fim da conversa chegam em sequência: só o primeiro encontra o Chat no topo.
                if (navController.currentBackStackEntry?.destination?.hasRoute<Chat>() == true) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(CHAT_END_MESSAGE_KEY, message)
                    navController.popBackStack()
                }
            })
        }
        composable<SuggestLandmark> {
            SuggestLandmarkScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
        composable<MyContributions> { MyContributionsScreen(onBack = { navController.popBackStack() }) }
        composable<Settings> { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable<ModerationQueue> { entry ->
            ModeratorOnly(isModerator, onDenied = { navController.popBackStack() }) {
                val handle = entry.savedStateHandle
                val decision by handle.getStateFlow<String?>(DECISION_ID_KEY, null).collectAsStateWithLifecycle()
                val viewModel: ModerationQueueViewModel = hiltViewModel()
                // O detalhe entrega a decisão aqui: o item sai da lista sem recarregar e o aviso aparece.
                LaunchedEffect(decision) {
                    decision?.let {
                        viewModel.decided(it, handle[DECISION_NOTICE_KEY])
                        handle[DECISION_ID_KEY] = null
                        handle[DECISION_NOTICE_KEY] = null
                    }
                }
                ModerationQueueScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLandmark = { navController.navigate(ModerationLandmark(it)) },
                    onOpenCuriosity = { navController.navigate(ModerationCuriosity(it)) },
                    viewModel = viewModel,
                )
            }
        }
        composable<ModerationLandmark> {
            ModeratorOnly(isModerator, onDenied = { navController.popBackStack(Main, inclusive = false) }) {
                ModerationDetailScreen(
                    kind = ModerationKind.LANDMARK,
                    onBack = { navController.popBackStack() },
                    onDecided = { navController.deliverDecision<ModerationLandmark>(it) },
                )
            }
        }
        composable<ModerationCuriosity> {
            ModeratorOnly(isModerator, onDenied = { navController.popBackStack(Main, inclusive = false) }) {
                ModerationDetailScreen(
                    kind = ModerationKind.CURIOSITY,
                    onBack = { navController.popBackStack() },
                    onDecided = { navController.deliverDecision<ModerationCuriosity>(it) },
                )
            }
        }
    }
}

/** Sem permissão de moderação, nada é composto (nem ViewModel, nem API) e a tela volta ao Perfil. */
@Composable
private fun ModeratorOnly(isModerator: Boolean?, onDenied: () -> Unit, content: @Composable () -> Unit) {
    when (isModerator) {
        true -> content()
        false -> LaunchedEffect(Unit) { onDenied() }
        null -> Unit // sem sessão: o efeito de sessão perdida leva ao Login
    }
}

private inline fun <reified T : Any> NavController.deliverDecision(decision: Decision) {
    // Só o detalhe no topo entrega e sai; um callback atrasado não deve mexer em outra tela.
    if (currentBackStackEntry?.destination?.hasRoute<T>() != true) return
    runCatching { getBackStackEntry<ModerationQueue>() }.getOrNull()?.savedStateHandle?.let {
        it[DECISION_NOTICE_KEY] = decision.notice
        it[DECISION_ID_KEY] = decision.id
    }
    popBackStack()
}

private fun NavController.navigateClearingStack(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}
