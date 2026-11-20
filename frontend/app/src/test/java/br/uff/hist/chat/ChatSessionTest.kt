package br.uff.hist.chat

import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeChatConnection
import br.uff.hist.testutil.FakeChatSocketFactory
import br.uff.hist.testutil.FakeLocationProvider
import br.uff.hist.testutil.FakeSessionStore
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val LANDMARK = GeoPoint(-22.5269, -41.9450)

/** Ponto [meters] ao norte do local (haversine ao longo do meridiano é exato). */
private fun at(meters: Double) = GeoPoint(LANDMARK.latitude + Math.toDegrees(meters / 6_371_000.0), LANDMARK.longitude)

private fun fix(meters: Double, mock: Boolean = false) = LocationFix(at(meters), 5f, mock)

private fun welcome(memberCount: Int? = null, maxLength: Int = 1000): String {
    val count = memberCount?.let { ""","member_count":$it""" } ?: ""
    return """{"type":"welcome","room":"lm1","landmark":{"id":"lm1","name":"Igreja"},
        "user":{"id":"u1","name":"Ana"},"join_radius_m":200,"leave_radius_m":300,
        "max_message_length":$maxLength$count}"""
}

private fun message(id: String, userId: String, name: String, text: String) =
    """{"type":"message","id":"$id","room":"lm1","user":{"id":"$userId","name":"$name"},
    "text":"$text","sent_at":"2026-11-20T12:00:00Z"}"""

private fun presence(event: String, userId: String, name: String, memberCount: Int? = null): String {
    val count = memberCount?.let { ""","member_count":$it""" } ?: ""
    return """{"type":"presence","event":"$event","room":"lm1","user":{"id":"$userId","name":"$name"},
        "sent_at":"2026-11-20T12:00:00Z"$count}"""
}

private fun serverError(code: String, msg: String) = """{"type":"error","code":"$code","message":"$msg"}"""

/** Conexão que registra, a cada envio, quantos itens a tela mostrava naquele instante. */
private class SpyConnection(
    private val inner: ChatConnection,
    private val onSend: (String) -> Unit,
) : ChatConnection by inner {
    override fun send(text: String): Boolean {
        onSend(text)
        return inner.send(text)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class Harness(val test: TestScope, config: ChatConfig) {
    val factory = FakeChatSocketFactory()
    val location = FakeLocationProvider().apply { currentResult = fix(50.0) }
    val store = FakeSessionStore(Session("tok", User("u1", "Ana", "Souza", "ana@x.com", setOf(Role.USER))))
    val sessionEvents = SessionEvents()
    val expired = mutableListOf<Unit>()

    /** (texto enviado, nº de itens visíveis no momento do envio) */
    val sendLog = mutableListOf<Pair<String, Int>>()

    lateinit var session: ChatSession

    init {
        val spyFactory = ChatSocketFactory { id, token, p ->
            SpyConnection(factory.connect(id, token, p)) { sendLog += it to session.state.value.items.size }
        }
        session = ChatSession("lm1", LANDMARK, spyFactory, location, store, sessionEvents, test.backgroundScope, config)
        test.backgroundScope.launch { sessionEvents.expired.collect { expired += it } }
    }

    val conn: FakeChatConnection get() = factory.lastConnection!!
    val state get() = session.state.value

    fun run() = test.runCurrent()

    fun advance(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    fun active(memberCount: Int? = null) {
        session.start()
        run()
        conn.serverSend(welcome(memberCount))
        run()
        assertEquals(ChatPhase.Active, state.phase)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
    private fun chatTest(config: ChatConfig = ChatConfig(), body: suspend Harness.() -> Unit) =
        runTest(StandardTestDispatcher()) { Harness(this, config).body() }

    // --- Início e welcome ---

    @Test fun connectsWithFreshFixAndToken() = chatTest {
        session.start()
        run()
        assertEquals(listOf(FakeChatSocketFactory.Connect("lm1", "tok", at(50.0))), factory.connects)
        assertEquals(ChatPhase.Connecting, state.phase)
        assertEquals(50.0, state.distanceM!!, 0.01)
    }

    @Test fun startWaitsForFirstUpdateWhenCurrentIsNull() = chatTest {
        location.currentResult = null
        session.start()
        run()
        assertEquals(0, factory.connectCount)
        location.emit(fix(40.0))
        run()
        assertEquals(at(40.0), factory.connects.single().point)
    }

    @Test fun mockFixAtStartEndsWithoutConnecting() = chatTest {
        location.currentResult = fix(50.0, mock = true)
        session.start()
        run()
        assertEquals(ChatPhase.Ended(EndReason.MOCK_LOCATION), state.phase)
        assertEquals(0, factory.connectCount)
    }

    @Test fun welcomeActivatesAndExposesRadii() = chatTest {
        session.start()
        run()
        conn.serverSend(welcome(memberCount = 3, maxLength = 500))
        run()
        assertEquals(ChatPhase.Active, state.phase)
        assertEquals("Igreja", state.landmarkName)
        assertEquals(200, state.joinRadiusM)
        assertEquals(300, state.leaveRadiusM)
        assertEquals(500, state.maxMessageLength)
        assertEquals(3, state.memberCount)
    }

    // --- Eventos do servidor ---

    @Test fun ownJoinPresenceIsIgnored() = chatTest {
        active(memberCount = 2)
        conn.serverSend(presence("join", "u1", "Ana", memberCount = 9))
        run()
        assertTrue(state.items.isEmpty())
        assertEquals(2, state.memberCount)
    }

    @Test fun presenceUpdatesMemberCountWhenPresent() = chatTest {
        active(memberCount = 2)
        conn.serverSend(presence("join", "u2", "Bia", memberCount = 3))
        conn.serverSend(presence("leave", "u3", "Carla", memberCount = 2))
        run()
        val notes = state.items.map { it as ChatItem.PresenceNote }
        assertEquals(listOf("Bia" to true, "Carla" to false), notes.map { it.name to it.joined })
        assertEquals(2, notes.map { it.key }.distinct().size)
        assertEquals(2, state.memberCount)
    }

    @Test fun memberCountStaysNullWithoutField() = chatTest {
        active()
        conn.serverSend(presence("join", "u2", "Bia"))
        run()
        assertNull(state.memberCount)
        assertEquals(1, state.items.size)
    }

    @Test fun messagesAreMarkedMineByUserId() = chatTest {
        active()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        conn.serverSend(message("m2", "u1", "Ana", "Olá"))
        run()
        assertEquals(
            listOf(
                ChatItem.Msg("m:m1", "Bia", "Oi", false, Instant.parse("2026-11-20T12:00:00Z")),
                ChatItem.Msg("m:m2", "Ana", "Olá", true, Instant.parse("2026-11-20T12:00:00Z")),
            ),
            state.items,
        )
    }

    @Test fun serverErrorBecomesNoticeWithoutEndingChat() = chatTest {
        active()
        conn.serverSend(serverError("rate_limited", "Calma, muitas mensagens"))
        run()
        assertEquals("Calma, muitas mensagens", state.notice)
        assertEquals(ChatPhase.Active, state.phase)
        session.consumeNotice()
        run()
        assertNull(state.notice)
    }

    // --- Durante Active ---

    @Test fun warnsWhenBeyondJoinRadius() = chatTest {
        active()
        assertFalse(state.warnLeaving)
        location.emit(fix(260.0))
        run()
        assertEquals(260.0, state.distanceM!!, 0.01)
        assertTrue(state.warnLeaving)
        location.emit(fix(150.0))
        run()
        assertFalse(state.warnLeaving)
    }

    @Test fun sendsLocationOnlyAfterTenMeters() = chatTest {
        active() // conectou em 50 m
        location.emit(fix(55.0))
        run()
        assertTrue(conn.sent.none { it.contains("\"location\"") })
        location.emit(fix(61.0))
        run()
        assertEquals(listOf(encodeLocation(at(61.0))), conn.sent.filter { it.contains("\"location\"") })
        location.emit(fix(65.0)) // 4 m do último envio
        run()
        assertEquals(1, conn.sent.count { it.contains("\"location\"") })
    }

    @Test fun mockFixDuringActiveEndsAndCloses() = chatTest {
        active()
        location.emit(fix(60.0, mock = true))
        run()
        assertEquals(ChatPhase.Ended(EndReason.MOCK_LOCATION), state.phase)
        assertEquals(1000, conn.closedByClient)
    }

    @Test fun pingsEveryThirtySeconds() = chatTest {
        active()
        advance(29_999)
        assertEquals(0, conn.sent.count { it == encodePing() })
        advance(90_000 - 29_999)
        assertEquals(3, conn.sent.count { it == encodePing() })
    }

    // --- Fechamentos e reconexão ---

    @Test fun close4403EndsOutOfRange() = chatTest {
        active()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        conn.serverClose(CLOSE_OUT_OF_RANGE)
        run()
        assertEquals(ChatPhase.Ended(EndReason.OUT_OF_RANGE), state.phase)
        assertTrue(state.items.isEmpty())
        advance(60_000)
        assertEquals(1, factory.connectCount)
    }

    @Test fun close4401ExpiresSessionAndDoesNotReconnect() = chatTest {
        run() // inscreve o coletor de `expired`
        active()
        conn.serverClose(CLOSE_UNAUTHORIZED)
        run()
        assertEquals(ChatPhase.Ended(EndReason.SESSION_EXPIRED), state.phase)
        assertEquals(1, expired.size)
        advance(60_000)
        assertEquals(1, factory.connectCount)
    }

    @Test fun close4404EndsNotFound() = chatTest {
        active()
        conn.serverClose(CLOSE_NOT_FOUND)
        run()
        assertEquals(ChatPhase.Ended(EndReason.NOT_FOUND), state.phase)
        advance(60_000)
        assertEquals(1, factory.connectCount)
    }

    @Test fun close4400EndsNotFound() = chatTest {
        active()
        conn.serverClose(CLOSE_BAD_REQUEST)
        run()
        assertEquals(ChatPhase.Ended(EndReason.NOT_FOUND), state.phase)
    }

    @Test fun abnormalCloseReconnectsWithBackoff() = chatTest {
        active()
        factory.nextOpens = false
        conn.serverClose(1001)
        run()
        assertEquals(ChatPhase.Reconnecting, state.phase)
        for ((wait, expectedCount) in listOf(1_000L to 2, 2_000L to 3, 4_000L to 4)) {
            advance(wait - 1)
            assertEquals(expectedCount - 1, factory.connectCount)
            factory.nextOpens = false
            advance(1)
            assertEquals(expectedCount, factory.connectCount)
            assertEquals(ChatPhase.Reconnecting, state.phase)
        }
    }

    @Test fun reconnectStopsIfUserLeftJoinRadius() = chatTest {
        active()
        location.currentResult = fix(250.0)
        conn.fail()
        run()
        assertEquals(ChatPhase.Ended(EndReason.OUT_OF_RANGE), state.phase)
        advance(60_000)
        assertEquals(1, factory.connectCount)
    }

    @Test fun backoffResetsAfterWelcome() = chatTest {
        active()
        factory.nextOpens = false
        conn.serverClose(1006)
        run()
        advance(1_000) // 2ª conexão falha antes de abrir
        assertEquals(2, factory.connectCount)
        advance(2_000) // 3ª conexão abre
        assertEquals(3, factory.connectCount)
        conn.serverSend(welcome())
        run()
        assertEquals(ChatPhase.Active, state.phase)
        conn.serverClose(1001)
        run()
        advance(999)
        assertEquals(3, factory.connectCount)
        advance(1)
        assertEquals(4, factory.connectCount)
    }

    @Test fun openedWithoutWelcomeCountsAsFailedAttempt() = chatTest {
        active()
        conn.serverClose(1001)
        run()
        advance(1_000) // abre, mas cai antes do welcome
        assertEquals(ChatPhase.Reconnecting, state.phase)
        conn.serverClose(1001)
        run()
        advance(1_999)
        assertEquals(2, factory.connectCount)
        advance(1)
        assertEquals(3, factory.connectCount)
    }

    // --- Segundo plano ---

    @Test fun backgroundBuffersWithoutShowing() = chatTest {
        active()
        session.onAppBackground()
        run()
        assertEquals(ChatPhase.Paused, state.phase)
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        conn.serverSend(presence("join", "u3", "Carla"))
        run()
        assertTrue(state.items.isEmpty())
    }

    @Test fun foregroundWithinRangeFlushesBufferAfterSendingLocation() = chatTest {
        active()
        session.onAppBackground()
        run()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        location.currentResult = fix(250.0) // fora do join, dentro do leave
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Active, state.phase)
        assertEquals(listOf("m:m1"), state.items.map { it.key })
        val locationSend = sendLog.single { it.first == encodeLocation(at(250.0)) }
        assertEquals(0, locationSend.second)
        assertTrue(state.warnLeaving)
    }

    @Test fun foregroundOutOfRangeEndsAndDiscards() = chatTest {
        active()
        session.onAppBackground()
        run()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        location.currentResult = fix(320.0)
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Ended(EndReason.OUT_OF_RANGE), state.phase)
        assertTrue(state.items.isEmpty())
        assertEquals(1000, conn.closedByClient)
        assertTrue(conn.sent.none { it.contains("\"location\"") })
    }

    @Test fun foregroundWithoutFixWaitsForFirstUpdate() = chatTest {
        active()
        session.onAppBackground()
        run()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        location.currentResult = null
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Paused, state.phase)
        assertTrue(state.items.isEmpty())
        location.emit(fix(70.0))
        run()
        assertEquals(ChatPhase.Active, state.phase)
        assertEquals(1, state.items.size)
    }

    @Test fun foregroundWithoutFixStillEndsAtFiveMinutes() = chatTest {
        active()
        session.onAppBackground()
        run()
        location.currentResult = null
        advance(60_000)
        session.onAppForeground()
        advance(239_999)
        assertEquals(ChatPhase.Paused, state.phase)
        advance(1)
        assertEquals(ChatPhase.Ended(EndReason.BACKGROUND_TIMEOUT), state.phase)
    }

    @Test fun backgroundTimeoutEndsAfterFiveMinutes() = chatTest {
        active()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        session.onAppBackground()
        run()
        advance(4 * 60_000 + 59_000)
        assertEquals(ChatPhase.Paused, state.phase)
        assertNull(conn.closedByClient)
        advance(1_000)
        assertEquals(ChatPhase.Ended(EndReason.BACKGROUND_TIMEOUT), state.phase)
        assertEquals(1000, conn.closedByClient)
        assertTrue(state.items.isEmpty())
    }

    @Test fun noReconnectAttemptsWhilePaused() = chatTest {
        active()
        session.onAppBackground()
        run()
        conn.serverClose(1006)
        run()
        advance(4 * 60_000)
        assertEquals(1, factory.connectCount)
        assertEquals(ChatPhase.Paused, state.phase)
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Reconnecting, state.phase)
        advance(1_000)
        assertEquals(2, factory.connectCount)
        conn.serverSend(welcome())
        advance(120_000) // o timer de 5 min foi cancelado
        assertEquals(ChatPhase.Active, state.phase)
    }

    @Test fun backgroundDuringConnectingPausesOnWelcome() = chatTest {
        session.start()
        run()
        session.onAppBackground()
        run()
        assertEquals(ChatPhase.Connecting, state.phase)
        conn.serverSend(welcome(maxLength = 500))
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        assertEquals(ChatPhase.Paused, state.phase)
        assertEquals(500, state.maxMessageLength)
        assertTrue(state.items.isEmpty())
        assertEquals(SendResult.NOT_ACTIVE, session.send("Oi"))
        // em segundo plano: nem coleta de posição nem ping
        location.emit(fix(150.0))
        advance(60_000)
        assertTrue(conn.sent.isEmpty())
        assertEquals(50.0, state.distanceM!!, 0.01)
        location.currentResult = fix(80.0)
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Active, state.phase)
        assertEquals(listOf("m:m1"), state.items.map { it.key })
        assertEquals(0, sendLog.single { it.first == encodeLocation(at(80.0)) }.second)
    }

    @Test fun backgroundTimeoutWhileConnectingEnds() = chatTest {
        session.start()
        run()
        session.onAppBackground()
        run()
        advance(5 * 60_000 - 1)
        assertEquals(ChatPhase.Connecting, state.phase)
        advance(1)
        assertEquals(ChatPhase.Ended(EndReason.BACKGROUND_TIMEOUT), state.phase)
        assertEquals(1000, conn.closedByClient)
    }

    @Test fun connectingDropInBackgroundReconnectsOnlyAfterForeground() = chatTest {
        session.start()
        run()
        session.onAppBackground()
        run()
        conn.serverClose(1006)
        advance(60_000)
        assertEquals(1, factory.connectCount)
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Reconnecting, state.phase)
        advance(1_000)
        assertEquals(2, factory.connectCount)
        conn.serverSend(welcome())
        advance(5 * 60_000) // timer cancelado ao voltar
        assertEquals(ChatPhase.Active, state.phase)
    }

    // --- Permissão de localização ---

    @Test fun noPermissionEndsSession() = chatTest {
        location.setPermission(false)
        session.start()
        run()
        assertEquals(ChatPhase.Ended(EndReason.NO_LOCATION_PERMISSION), state.phase)
        assertEquals(0, factory.connectCount)
    }

    @Test fun noPermissionBeforeReconnectEnds() = chatTest {
        active()
        location.setPermission(false)
        conn.serverClose(1001)
        run()
        advance(60_000)
        assertEquals(ChatPhase.Ended(EndReason.NO_LOCATION_PERMISSION), state.phase)
        assertEquals(1, factory.connectCount)
    }

    @Test fun noPermissionOnResumeEndsAndCloses() = chatTest {
        active()
        session.onAppBackground()
        run()
        location.setPermission(false)
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Ended(EndReason.NO_LOCATION_PERMISSION), state.phase)
        assertEquals(1000, conn.closedByClient)
    }

    // --- Saída ---

    @Test fun leaveClosesEmptiesAndIsIdempotent() = chatTest {
        active()
        conn.serverSend(message("m1", "u2", "Bia", "Oi"))
        run()
        session.leave()
        run()
        assertEquals(ChatPhase.Ended(EndReason.LEFT), state.phase)
        assertEquals(1000, conn.closedByClient)
        assertTrue(state.items.isEmpty())
        session.leave()
        session.start()
        session.onAppForeground()
        run()
        assertEquals(ChatPhase.Ended(EndReason.LEFT), state.phase)
        assertEquals(1, factory.connectCount)
    }

    // --- Envio (Review Focus 4) ---

    @Test fun sendBlankIsRejected() = chatTest {
        active()
        assertEquals(SendResult.BLANK, session.send("   "))
    }

    @Test fun sendTooLongIsRejected() = chatTest {
        active()
        assertEquals(SendResult.TOO_LONG, session.send("a".repeat(1001)))
        assertEquals(SendResult.SENT, session.send("  " + "a".repeat(1000) + "  "))
    }

    @Test fun sendEmojiCountsCodePoints() = chatTest {
        active()
        assertEquals(SendResult.SENT, session.send("👍"))
        assertEquals(SendResult.SENT, session.send("👍".repeat(1000)))
        assertEquals(SendResult.TOO_LONG, session.send("👍".repeat(1001)))
        run()
        assertTrue(encodeMessage("👍") in conn.sent)
    }

    @Test fun sendTrimsText() = chatTest {
        active()
        assertEquals(SendResult.SENT, session.send("  Oi  "))
        run()
        assertTrue(encodeMessage("Oi") in conn.sent)
    }

    @Test fun sendBeforeWelcomeIsNotActive() = chatTest {
        session.start()
        run()
        assertEquals(SendResult.NOT_ACTIVE, session.send("Oi"))
        run()
        assertTrue(conn.sent.isEmpty())
    }

    @Test fun serverMessageTooLongBecomesNoticeAndStaysActive() = chatTest {
        active()
        conn.serverSend(serverError("message_too_long", "Mensagem longa demais"))
        run()
        assertEquals("Mensagem longa demais", state.notice)
        assertEquals(ChatPhase.Active, state.phase)
    }
}
