package br.uff.hist.testutil

import br.uff.hist.chat.ChatConnection
import br.uff.hist.chat.ChatSocketFactory
import br.uff.hist.chat.SocketEvent
import br.uff.hist.core.model.GeoPoint
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeChatSocketFactory : ChatSocketFactory {
    data class Connect(val landmarkId: String, val token: String, val point: GeoPoint)

    val connects = mutableListOf<Connect>()
    val connections = mutableListOf<FakeChatConnection>()
    val connectCount: Int get() = connects.size
    val lastConnection: FakeChatConnection? get() = connections.lastOrNull()

    override fun connect(landmarkId: String, token: String, at: GeoPoint): ChatConnection {
        connects += Connect(landmarkId, token, at)
        return FakeChatConnection().also { connections += it }
    }
}

class FakeChatConnection : ChatConnection {
    private val channel = Channel<SocketEvent>(Channel.UNLIMITED)
    private var done = false

    val sent = mutableListOf<String>()
    var closedByClient: Int? = null
        private set

    init { channel.trySend(SocketEvent.Opened) }

    override val events: Flow<SocketEvent> = channel.receiveAsFlow()

    override fun send(text: String): Boolean {
        if (done) return false
        sent += text
        return true
    }

    override fun close(code: Int, reason: String) {
        closedByClient = code
        finish(SocketEvent.Closed(code, reason))
    }

    fun serverSend(json: String) {
        if (!done) channel.trySend(SocketEvent.Text(json))
    }

    fun serverClose(code: Int, reason: String = "") = finish(SocketEvent.Closed(code, reason))

    fun fail(error: Throwable = IOException()) = finish(SocketEvent.Failure(error))

    private fun finish(event: SocketEvent) {
        if (done) return
        done = true
        channel.trySend(event)
        channel.close()
    }
}
