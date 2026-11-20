package br.uff.hist.chat

import br.uff.hist.BuildConfig
import br.uff.hist.core.model.GeoPoint
import java.net.URLEncoder
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpChatSocketFactory(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : ChatSocketFactory {
    @Inject constructor(client: OkHttpClient) : this(client, BuildConfig.API_BASE_URL)

    override fun connect(landmarkId: String, token: String, at: GeoPoint): ChatConnection =
        OkHttpChatConnection(client, buildChatUrl(baseUrl, landmarkId, token, at))
}

internal fun buildChatUrl(baseUrl: String, landmarkId: String, token: String, at: GeoPoint): String {
    val base = when {
        baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
        baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
        else -> baseUrl
    }.let { if (it.endsWith("/")) it else "$it/" }
    val id = URLEncoder.encode(landmarkId, "UTF-8").replace("+", "%20")
    val t = URLEncoder.encode(token, "UTF-8").replace("+", "%20")
    val lat = plain(at.latitude)
    val lon = plain(at.longitude)
    return "${base}chat/$id?token=$t&lat=$lat&lon=$lon"
}

/** Decimal com ponto e sem notação científica, independente do locale. */
private fun plain(v: Double): String = BigDecimal.valueOf(v).toPlainString()

/**
 * O WebSocket é aberto já no construtor; os eventos vão para um `Channel.UNLIMITED`,
 * então nada se perde antes de o coletor se inscrever (e por isso só há um coletor).
 */
private class OkHttpChatConnection(client: OkHttpClient, url: String) : ChatConnection {
    private val channel = Channel<SocketEvent>(Channel.UNLIMITED)
    private val terminated = AtomicBoolean(false)

    override val events: Flow<SocketEvent> = channel.receiveAsFlow()

    private val webSocket: WebSocket = client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                channel.trySend(SocketEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                channel.trySend(SocketEvent.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                terminate(SocketEvent.Closed(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                terminate(SocketEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                terminate(SocketEvent.Failure(t))
            }
        },
    )

    private fun terminate(event: SocketEvent) {
        if (terminated.compareAndSet(false, true)) {
            channel.trySend(event)
            channel.close()
        }
    }

    override fun send(text: String): Boolean = webSocket.send(text)

    override fun close(code: Int, reason: String) {
        webSocket.close(code, reason.ifEmpty { null })
        terminate(SocketEvent.Closed(code, reason))
    }
}
