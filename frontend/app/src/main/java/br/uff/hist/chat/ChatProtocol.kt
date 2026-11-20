package br.uff.hist.chat

import br.uff.hist.core.model.GeoPoint
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ChatUser(val id: String, val name: String)

sealed interface ServerEvent {
    data class Welcome(
        val room: String,
        val landmarkName: String,
        val me: ChatUser,
        val joinRadiusM: Int,
        val leaveRadiusM: Int,
        val maxMessageLength: Int,
        val memberCount: Int?,
    ) : ServerEvent

    data class Message(val id: String, val user: ChatUser, val text: String, val sentAt: Instant) : ServerEvent
    data class Presence(val joined: Boolean, val user: ChatUser, val memberCount: Int?) : ServerEvent
    data class Error(val code: String, val message: String) : ServerEvent
    data object Pong : ServerEvent
}

/** Interpreta um frame do servidor. Nunca lança: qualquer problema devolve `null`. */
fun parseServerEvent(raw: String): ServerEvent? = try {
    val obj = Json.parseToJsonElement(raw).jsonObject
    when (obj.string("type")) {
        "welcome" -> ServerEvent.Welcome(
            room = obj.string("room"),
            landmarkName = obj.getValue("landmark").jsonObject.string("name"),
            me = obj.getValue("user").jsonObject.toChatUser(),
            joinRadiusM = obj.getValue("join_radius_m").jsonPrimitive.int,
            leaveRadiusM = obj.getValue("leave_radius_m").jsonPrimitive.int,
            maxMessageLength = obj.getValue("max_message_length").jsonPrimitive.int,
            memberCount = obj.memberCount(),
        )
        "message" -> ServerEvent.Message(
            id = obj.string("id"),
            user = obj.getValue("user").jsonObject.toChatUser(),
            text = obj.string("text"),
            sentAt = OffsetDateTime.parse(obj.string("sent_at")).toInstant(),
        )
        "presence" -> ServerEvent.Presence(
            joined = when (obj.string("event")) {
                "join" -> true
                "leave" -> false
                else -> error("evento de presença desconhecido")
            },
            user = obj.getValue("user").jsonObject.toChatUser(),
            memberCount = obj.memberCount(),
        )
        "error" -> ServerEvent.Error(obj.string("code"), obj.string("message"))
        "pong" -> ServerEvent.Pong
        else -> null
    }
} catch (_: Exception) {
    null
}

private fun JsonObject.string(key: String): String {
    val p = getValue(key).jsonPrimitive
    check(p.isString) { "$key não é texto" }
    return p.content
}

private fun JsonObject.toChatUser() = ChatUser(string("id"), string("name"))

private fun JsonObject.memberCount(): Int? = (get("member_count") as? JsonPrimitive)?.intOrNull

fun encodeMessage(text: String): String = buildJsonObject {
    put("type", "message")
    put("text", text)
}.toString()

fun encodeLocation(p: GeoPoint): String = buildJsonObject {
    put("type", "location")
    put("lat", p.latitude)
    put("lon", p.longitude)
}.toString()

fun encodePing(): String = buildJsonObject { put("type", "ping") }.toString()
