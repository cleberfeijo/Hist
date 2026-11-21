package br.uff.hist.explore

import br.uff.hist.core.model.CHAT_JOIN_RADIUS_M
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.distanceMeters

data class LandmarkItem(val landmark: Landmark, val distanceM: Double?, val chatAvailable: Boolean)

/** Distância sempre a partir do usuário; sem usuário mantém a ordem recebida e sem chat. [chatEnabled] `false` (localização só aproximada) tira o chat de todos. */
fun toItems(landmarks: List<Landmark>, user: GeoPoint?, chatEnabled: Boolean = true): List<LandmarkItem> {
    if (user == null) return landmarks.map { LandmarkItem(it, null, false) }
    return landmarks
        .map {
            val d = distanceMeters(user, it.location)
            LandmarkItem(it, d, chatEnabled && d <= CHAT_JOIN_RADIUS_M)
        }
        .sortedBy { it.distanceM }
}
