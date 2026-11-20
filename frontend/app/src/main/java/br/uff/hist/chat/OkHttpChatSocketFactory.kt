package br.uff.hist.chat

import br.uff.hist.BuildConfig
import br.uff.hist.core.model.GeoPoint
import java.net.URLEncoder
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
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

/** Mantém o socket vivo atrás de NATs ociosos durante a janela de 5 min em segundo plano. */
private const val CHAT_PING_INTERVAL_S = 20L

class OkHttpChatSocketFactory(
    internal val client: OkHttpClient,
    private val baseUrl: String,
) : ChatSocketFactory {
    /** Deriva do cliente compartilhado (mesmo pool e interceptors) só para ajustar o ping do WebSocket. */
    @Inject constructor(client: OkHttpClient) : this(
        client.newBuilder().pingInterval(CHAT_PING_INTERVAL_S, TimeUnit.SECONDS).build(),
        BuildConfig.API_BASE_URL,
    )

    override fun connect(landmarkId: String, token: String, at: GeoPoint): ChatConnection =
        OkHttpChatConnection(client, buildChatUrl(baseUrl, landmarkId, at), token)
}

internal fun buildChatUrl(baseUrl: String, landmarkId: String, at: GeoPoint): String {
    val base = when {
        baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
        baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
        else -> baseUrl
    }.let { if (it.endsWith("/")) it else "$it/" }
    val id = URLEncoder.encode(landmarkId, "UTF-8").replace("+", "%20")
    val lat = plain(at.latitude)
    val lon = plain(at.longitude)
    return "${base}chat/$id?lat=$lat&lon=$lon"
}

/** Decimal com ponto e sem notação científica, independente do locale. */
private fun plain(v: Double): String = BigDecimal.valueOf(v).toPlainString()

/**
 * O WebSocket é aberto já no construtor; os eventos vão para um `Channel.UNLIMITED`,
 * então nada se perde antes de o coletor se inscrever (e por isso só há um coletor).
 */
private class OkHttpChatConnection(client: OkHttpClient, url: String, token: String) : ChatConnection {
    private val channel = Channel<SocketEvent>(Channel.UNLIMITED)
    private val terminated = AtomicBoolean(false)

    override val events: Flow<SocketEvent> = channel.receiveAsFlow()

    private val webSocket: WebSocket = client.newWebSocket(
        // O JWT vai no header (não na URL, para não vazar em logs); header() substitui o do AuthInterceptor.
        Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                channel.trySend(SocketEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                channel.trySend(SocketEvent.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Códigos como 1005/1006 não podem ser enviados; responde 1000 e reporta o código do servidor.
                terminate(SocketEvent.Closed(code, reason))
                webSocket.close(1000, null)
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
        terminate(SocketEvent.Closed(code, reason))
        try {
            webSocket.close(code, reason.ifEmpty { null })
        } catch (_: IllegalArgumentException) {
            webSocket.cancel() // código/motivo inválido: o fluxo já terminou; derruba a conexão
        }
    }
}
