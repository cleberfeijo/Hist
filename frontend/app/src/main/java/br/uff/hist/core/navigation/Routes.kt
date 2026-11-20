package br.uff.hist.core.navigation

import br.uff.hist.core.session.Session
import kotlinx.serialization.Serializable

@Serializable data object Login
@Serializable data object Register
@Serializable data object LocationOnboarding
@Serializable data object Main
@Serializable data class LandmarkDetail(val id: String)
@Serializable data class Chat(val landmarkId: String)
@Serializable data object SuggestLandmark
@Serializable data object MyContributions
@Serializable data object Settings
@Serializable data object ModerationQueue
@Serializable data class ModerationLandmark(val id: String)
@Serializable data class ModerationCuriosity(val id: String)

/** Destino inicial (e destino pós-login) conforme a sessão e o onboarding de localização. */
fun rootDestination(session: Session?, onboardingDone: Boolean): Any = when {
    session == null -> Login
    !onboardingDone -> LocationOnboarding
    else -> Main
}
