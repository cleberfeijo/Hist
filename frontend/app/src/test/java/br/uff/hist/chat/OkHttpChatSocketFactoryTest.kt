package br.uff.hist.chat

import br.uff.hist.core.model.GeoPoint
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpChatSocketFactoryTest {
    private val server = MockWebServer()
    private val serverSockets = LinkedBlockingQueue<WebSocket>()
    private val serverReceived = LinkedBlockingQueue<String>()
    private val client = OkHttpClient()

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { serverSockets.add(webSocket) }
        override fun onMessage(webSocket: WebSocket, text: String) { serverReceived.add(text) }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
    }

    @Before fun setUp() {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun factory(base: String = server.url("/v1/").toString()) =
        OkHttpChatSocketFactory(client, base)

    private fun serverSocket(): WebSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

    @Test fun buildsUpgradeUrlAndDeliversEvents() = runBlocking {
        val conn = factory().connect("abc", "t", GeoPoint(-22.5, -41.9))
        val ws = serverSocket()
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/abc?token=t&lat=-22.5&lon=-41.9", request.path)

        ws.send("""{"type":"pong"}""")
        ws.close(1000, "fim")
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(SocketEvent.Opened, events[0])
        assertEquals(SocketEvent.Text("""{"type":"pong"}"""), events[1])
        assertEquals(SocketEvent.Closed(1000, "fim"), events[2])
        assertEquals(3, events.size)
    }

    @Test fun urlEncodesTokenAndFormatsCoordinatesWithDot() = runBlocking {
        val conn = factory().connect("abc", "a b&c=d", GeoPoint(-22.0, -41.5))
        serverSocket()
        val path = server.takeRequest(5, TimeUnit.SECONDS)!!.path
        assertEquals("/v1/chat/abc?token=a%20b%26c%3Dd&lat=-22.0&lon=-41.5", path)
        conn.close()
        withTimeout(5_000) { conn.events.toList() }
        Unit
    }

    @Test fun serverCloseWithCustomCodeEndsFlow() = runBlocking {
        val conn = factory().connect("abc", "t", GeoPoint(0.0, 0.0))
        val ws = serverSocket()
        ws.close(CLOSE_OUT_OF_RANGE, "longe")
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(SocketEvent.Closed(4403, "longe"), events.last())
        assertEquals(1, events.filterIsInstance<SocketEvent.Closed>().size)
    }

    @Test fun sendReachesServerAndFailsAfterClose() = runBlocking {
        val conn = factory().connect("abc", "t", GeoPoint(0.0, 0.0))
        serverSocket()
        withTimeout(5_000) { conn.events.first { it == SocketEvent.Opened } }
        assertTrue(conn.send("oi"))
        assertEquals("oi", serverReceived.poll(5, TimeUnit.SECONDS))

        conn.close(1000, "tchau")
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(1, events.filterIsInstance<SocketEvent.Closed>().size)
        assertFalse(conn.send("depois"))
    }

    @Test fun connectionFailureEmitsFailureAndEnds() = runBlocking {
        val conn = factory("http://127.0.0.1:1/v1/").connect("abc", "t", GeoPoint(0.0, 0.0))
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(1, events.size)
        assertTrue(events[0] is SocketEvent.Failure)
    }

    @Test fun httpsBaseBecomesWss() {
        assertEquals(
            "wss://h.example/v1/chat/x?token=t&lat=1.0&lon=2.0",
            buildChatUrl("https://h.example/v1/", "x", "t", GeoPoint(1.0, 2.0)),
        )
    }
}
