package br.uff.hist.chat

import br.uff.hist.core.model.GeoPoint
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatProtocolTest {
    private val welcome = """{"type": "welcome", "room": "r1", "landmark": {"id": "r1", "name": "Igreja"},
        "user": {"id": "u1", "name": "Ana Souza"}, "join_radius_m": 200, "leave_radius_m": 300,
        "max_message_length": 1000, "history_enabled": false}"""

    @Test
    fun parsesWelcomeWithoutMemberCount() {
        assertEquals(
            ServerEvent.Welcome("r1", "Igreja", ChatUser("u1", "Ana Souza"), 200, 300, 1000, null),
            parseServerEvent(welcome),
        )
    }

    @Test
    fun parsesWelcomeWithMemberCount() {
        val raw = welcome.replace("\"history_enabled\"", "\"member_count\": 3, \"history_enabled\"")
        assertEquals(3, (parseServerEvent(raw) as ServerEvent.Welcome).memberCount)
    }

    @Test
    fun parsesMessage() {
        val raw = """{"type":"message","id":"m1","room":"r1","user":{"id":"u2","name":"Bia"},
            "text":"Oi","sent_at":"2026-11-20T12:00:00Z"}"""
        assertEquals(
            ServerEvent.Message("m1", ChatUser("u2", "Bia"), "Oi", Instant.parse("2026-11-20T12:00:00Z")),
            parseServerEvent(raw),
        )
    }

    @Test
    fun parsesMessageWithOffsetDate() {
        val raw = """{"type":"message","id":"m1","room":"r1","user":{"id":"u2","name":"Bia"},
            "text":"Oi","sent_at":"2026-11-20T09:00:00.123456-03:00"}"""
        assertEquals(
            Instant.parse("2026-11-20T12:00:00.123456Z"),
            (parseServerEvent(raw) as ServerEvent.Message).sentAt,
        )
    }

    @Test
    fun parsesPresenceJoinAndLeave() {
        fun raw(event: String, extra: String = "") =
            """{"type":"presence","event":"$event","room":"r1","user":{"id":"u2","name":"Bia"},
            "sent_at":"2026-11-20T12:00:00Z"$extra}"""
        assertEquals(
            ServerEvent.Presence(true, ChatUser("u2", "Bia"), null),
            parseServerEvent(raw("join")),
        )
        assertEquals(false, (parseServerEvent(raw("leave")) as ServerEvent.Presence).joined)
        assertEquals(
            5,
            (parseServerEvent(raw("join", ",\"member_count\":5")) as ServerEvent.Presence).memberCount,
        )
    }

    @Test
    fun parsesErrorAndPong() {
        assertEquals(
            ServerEvent.Error("rate_limited", "devagar"),
            parseServerEvent("""{"type":"error","code":"rate_limited","message":"devagar"}"""),
        )
        assertEquals(ServerEvent.Pong, parseServerEvent("""{"type":"pong"}"""))
    }

    @Test
    fun invalidInputsReturnNull() {
        assertNull(parseServerEvent("""{"type":"novo"}"""))
        assertNull(parseServerEvent("lixo"))
        assertNull(parseServerEvent(""))
        assertNull(parseServerEvent("[]"))
        assertNull(parseServerEvent("""{"type":"welcome","room":"r1"}"""))
        assertNull(parseServerEvent("""{"type":"presence","event":"dance","room":"r","user":{"id":"u","name":"n"},"sent_at":"2026-11-20T12:00:00Z"}"""))
        assertNull(
            parseServerEvent(
                """{"type":"message","id":"m","room":"r","user":{"id":"u","name":"n"},"text":"x","sent_at":"ontem"}""",
            ),
        )
    }

    @Test
    fun encodesClientMessages() {
        val msg = Json.parseToJsonElement(encodeMessage("Olá \"mundo\"")).jsonObject
        assertEquals("message", msg["type"]!!.jsonPrimitive.content)
        assertEquals("Olá \"mundo\"", msg["text"]!!.jsonPrimitive.content)

        val loc = Json.parseToJsonElement(encodeLocation(GeoPoint(-22.52, -41.94))).jsonObject
        assertEquals("location", loc["type"]!!.jsonPrimitive.content)
        assertEquals(-22.52, loc["lat"]!!.jsonPrimitive.double, 0.0)
        assertEquals(-41.94, loc["lon"]!!.jsonPrimitive.double, 0.0)

        assertEquals("ping", Json.parseToJsonElement(encodePing()).jsonObject["type"]!!.jsonPrimitive.content)
    }
}
