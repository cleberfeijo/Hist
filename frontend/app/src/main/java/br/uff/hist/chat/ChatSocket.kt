package br.uff.hist.chat

import br.uff.hist.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow

const val CLOSE_BAD_REQUEST = 4400
const val CLOSE_UNAUTHORIZED = 4401
const val CLOSE_OUT_OF_RANGE = 4403
const val CLOSE_NOT_FOUND = 4404

sealed interface SocketEvent {
    data object Opened : SocketEvent
    data class Text(val raw: String) : SocketEvent
    data class Closed(val code: Int, val reason: String) : SocketEvent
    data class Failure(val error: Throwable) : SocketEvent
}

interface ChatConnection {
    /** Emite Opened, Text*, e um único Closed ou Failure; depois completa. Um coletor por conexão.
     * Quem abandona uma conexão deve sempre chamar [close], senão o socket fica aberto. */
    val events: Flow<SocketEvent>
    fun send(text: String): Boolean
    fun close(code: Int = 1000, reason: String = "")
}

fun interface ChatSocketFactory {
    fun connect(landmarkId: String, token: String, at: GeoPoint): ChatConnection
}
