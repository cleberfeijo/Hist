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
import br.uff.hist.core.designsystem.components.LoadingState
import br.uff.hist.landmark.LandmarkDetailScreen
import br.uff.hist.onboarding.LocationOnboardingScreen
import kotlinx.coroutines.launch

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
            )
        }
        composable<LandmarkDetail> {
            LandmarkDetailScreen(
                onBack = { navController.popBackStack() },
                onEnterChat = { navController.navigate(Chat(it)) },
            )
        }
        composable<Chat> { PlaceholderScreen() }
        composable<SuggestLandmark> { PlaceholderScreen() }
        composable<MyContributions> { PlaceholderScreen() }
        composable<Settings> { PlaceholderScreen() }
        composable<ModerationQueue> { PlaceholderScreen() }
        composable<ModerationLandmark> { PlaceholderScreen() }
        composable<ModerationCuriosity> { PlaceholderScreen() }
    }
}

private fun NavController.navigateClearingStack(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}
