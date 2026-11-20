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
        assertEquals("/v1/chat/abc?lat=-22.5&lon=-41.9", request.path)
        assertEquals("Bearer t", request.getHeader("Authorization"))

        ws.send("""{"type":"pong"}""")
        ws.close(1000, "fim")
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(SocketEvent.Opened, events[0])
        assertEquals(SocketEvent.Text("""{"type":"pong"}"""), events[1])
        assertEquals(SocketEvent.Closed(1000, "fim"), events[2])
        assertEquals(3, events.size)
    }

    @Test fun formatsCoordinatesWithDotAndKeepsTokenOutOfUrl() = runBlocking {
        val conn = factory().connect("abc", "a b&c=d", GeoPoint(-22.0, -41.5))
        serverSocket()
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/abc?lat=-22.0&lon=-41.5", request.path)
        assertEquals(listOf("Bearer a b&c=d"), request.headers.values("Authorization"))
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

    @Test fun closeFrameWithoutStatusCodeYieldsClosed1005() = runBlocking {
        val raw = java.net.ServerSocket(0)
        val thread = Thread {
            raw.accept().use { sock ->
                val input = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                var key = ""
                while (true) {
                    val line = input.readLine() ?: return@use
                    if (line.isEmpty()) break
                    if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) key = line.substringAfter(":").trim()
                }
                val accept = java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                )
                val out = sock.getOutputStream()
                out.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(),
                )
                out.write(byteArrayOf(0x88.toByte(), 0x00)) // close sem código
                out.flush()
                Thread.sleep(500)
            }
        }.also { it.start() }
        try {
            val conn = factory("http://127.0.0.1:${raw.localPort}/v1/").connect("abc", "t", GeoPoint(0.0, 0.0))
            val events = withTimeout(5_000) { conn.events.toList() }
            assertEquals(SocketEvent.Opened, events[0])
            assertEquals(1005, (events.last() as SocketEvent.Closed).code)
            assertEquals(2, events.size)
        } finally {
            raw.close()
            thread.join()
        }
    }

    @Test fun closeWithInvalidCodeStillEndsFlow() = runBlocking {
        val conn = factory().connect("abc", "t", GeoPoint(0.0, 0.0))
        serverSocket()
        conn.close(1005, "inválido")
        val events = withTimeout(5_000) { conn.events.toList() }
        assertEquals(SocketEvent.Closed(1005, "inválido"), events.last())
    }

    @Test fun httpsBaseBecomesWss() {
        assertEquals(
            "wss://h.example/v1/chat/x?lat=1.0&lon=2.0",
            buildChatUrl("https://h.example/v1/", "x", GeoPoint(1.0, 2.0)),
        )
    }
}
