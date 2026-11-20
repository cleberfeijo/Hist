package br.uff.hist.chat

import br.uff.hist.core.location.CHAT_INTERVAL_MS
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.location.LocationPriority
import br.uff.hist.core.location.LocationProvider
import br.uff.hist.core.model.BACKGROUND_TOLERANCE_MS
import br.uff.hist.core.model.CHAT_JOIN_RADIUS_M
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.LOCATION_MIN_DISPLACEMENT_M
import br.uff.hist.core.model.PING_INTERVAL_MS
import br.uff.hist.core.model.distanceMeters
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.core.session.SessionStore
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatConfig(
    val backgroundToleranceMs: Long = BACKGROUND_TOLERANCE_MS,
    val pingIntervalMs: Long = PING_INTERVAL_MS,
    val minDisplacementM: Double = LOCATION_MIN_DISPLACEMENT_M,
    val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000),
)

enum class EndReason { OUT_OF_RANGE, BACKGROUND_TIMEOUT, SESSION_EXPIRED, NOT_FOUND, MOCK_LOCATION, LEFT, NO_LOCATION_PERMISSION }

sealed interface ChatPhase {
    data object Connecting : ChatPhase
    data object Active : ChatPhase
    data object Paused : ChatPhase
    data object Reconnecting : ChatPhase
    data class Ended(val reason: EndReason) : ChatPhase
}

sealed interface ChatItem {
    val key: String
    data class Msg(override val key: String, val author: String, val text: String, val mine: Boolean, val sentAt: Instant) : ChatItem
    data class PresenceNote(override val key: String, val name: String, val joined: Boolean) : ChatItem
}

data class ChatState(
    val phase: ChatPhase,
    val landmarkName: String?,
    val memberCount: Int?,
    val items: List<ChatItem>,
    val distanceM: Double?,
    val warnLeaving: Boolean,
    val joinRadiusM: Int,
    val leaveRadiusM: Int,
    val maxMessageLength: Int,
    val notice: String?,
)

enum class SendResult { SENT, BLANK, TOO_LONG, NOT_ACTIVE }

/** Padrões do cliente até o `welcome` chegar (é ele quem manda). */
private const val DEFAULT_LEAVE_RADIUS_M = 300
private const val DEFAULT_MAX_MESSAGE_LENGTH = 1000
private const val MAX_NOTICE_LENGTH = 200
private const val CLOSE_NORMAL = 1000

/**
 * Sessão efêmera do chat de proximidade de um ponto (spec 5.3 "Chat"). Kotlin puro, sem tipos do Android.
 *
 * **Dono único do estado:** toda transição acontece no ator [handle], que consome a caixa [inbox] numa
 * única corrotina em [scope]. Os métodos públicos e os jobs auxiliares (coleta do socket, localização,
 * ping, timers, reconexão) só postam mensagens; cada mensagem assíncrona leva um token e é ignorada se o
 * job que a gerou já foi substituído ou cancelado. Há no máximo uma conexão viva; toda conexão abandonada
 * recebe `close(1000)`. Depois de [ChatPhase.Ended] tudo vira no-op.
 * Por isso os métodos públicos são assíncronos: o efeito aparece em [state] quando o ator processa a
 * mensagem (só o [SendResult] de [send] é imediato, calculado sobre o [state] atual).
 *
 * Decisões que a spec não fixa:
 * - `start()`/`onAppForeground()` sem fix atual esperam o primeiro fix de `updates(HIGH)`.
 * - Sem permissão de localização (no `start()`, antes de cada tentativa de reconexão ou ao voltar ao app,
 *   ou se `updates` completar vazio) → fecha a conexão e `Ended(NO_LOCATION_PERMISSION)`; nunca fica preso.
 * - "Em segundo plano" ([inBackground]) vale em qualquer fase: o timer de 5 minutos começa já em
 *   `Connecting`. Se o `welcome` chegar em segundo plano, raios/limite são aplicados e a sessão vai direto
 *   para `Paused` (o que vier depois fica guardado). Em segundo plano nunca há tentativa de reconexão.
 * - Voltando ao app ainda em `Connecting` (sem `welcome`, nada guardado), só cancela o timer e retoma.
 *   Já em `Paused`, o timer só é cancelado quando o fix novo chega; até lá ele ainda vale.
 * - Só `welcome` torna a sessão `Active`. Conexão que termina antes do `welcome` conta como tentativa
 *   falha (o backoff continua); `welcome` zera o backoff. A tentativa k espera `backoffMs[min(k, último)]`,
 *   depois de checar `current(HIGH)`: fix nulo → tenta de novo na próxima espera; mock → `MOCK_LOCATION`;
 *   além de `joinRadiusM` → `OUT_OF_RANGE`.
 * - Sem token salvo ao conectar → `notifyExpired()` + `SESSION_EXPIRED` (mesmo tratamento do `4401`).
 */
class ChatSession(
    private val landmarkId: String,
    private val landmarkPoint: GeoPoint,
    private val factory: ChatSocketFactory,
    private val location: LocationProvider,
    private val store: SessionStore,
    private val sessionEvents: SessionEvents,
    private val scope: CoroutineScope,
    private val config: ChatConfig = ChatConfig(),
) {
    private val _state = MutableStateFlow(
        ChatState(
            phase = ChatPhase.Connecting,
            landmarkName = null,
            memberCount = null,
            items = emptyList(),
            distanceM = null,
            warnLeaving = false,
            joinRadiusM = CHAT_JOIN_RADIUS_M,
            leaveRadiusM = DEFAULT_LEAVE_RADIUS_M,
            maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH,
            notice = null,
        ),
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private sealed interface Input {
        data object Start : Input
        data object Background : Input
        data object Foreground : Input
        data object Leave : Input
        data object ConsumeNotice : Input
        data class Send(val text: String) : Input
        data class StartFix(val fix: LocationFix?) : Input
        data class Socket(val token: Int, val event: SocketEvent) : Input
        data class TrackFix(val token: Int, val fix: LocationFix) : Input
        data class PingTick(val token: Int) : Input
        data class BackgroundTimeout(val token: Int) : Input
        data class ForegroundFix(val token: Int, val fix: LocationFix?) : Input
        data class ReconnectChecked(val token: Int, val fix: LocationFix?) : Input
        data class ReconnectDue(val token: Int, val fix: LocationFix?) : Input
    }

    private val inbox = Channel<Input>(Channel.UNLIMITED)

    // ---- Estado do ator: lido e escrito só dentro de handle() ----
    private var started = false
    private var ended = false
    private var nextToken = 0

    private var conn: ChatConnection? = null
    private var connToken = 0
    private var welcomed = false // a conexão atual já recebeu `welcome`
    private var me: ChatUser? = null
    private var attempt = 0
    private var lastSentPoint: GeoPoint? = null

    private var inBackground = false
    private var paused = false // fase Paused: eventos guardados, não exibidos
    private var pendingReconnect = false // a conexão caiu em segundo plano
    private val buffer = mutableListOf<ServerEvent>()
    private var presenceSeq = 0

    private var trackToken = 0
    private var reconnectToken = 0
    private var timerToken = 0
    private var foregroundToken = 0

    private var startJob: Job? = null
    private var connJob: Job? = null
    private var locationJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var timerJob: Job? = null
    private var foregroundJob: Job? = null

    init {
        scope.launch {
            try {
                for (input in inbox) handle(input)
            } finally {
                // escopo cancelado (tela fechada) sem leave(): não deixar o socket aberto
                conn?.close(CLOSE_NORMAL)
            }
        }
    }

    fun start() = post(Input.Start)

    fun onAppBackground() = post(Input.Background)

    fun onAppForeground() = post(Input.Foreground)

    fun leave() = post(Input.Leave)

    fun consumeNotice() = post(Input.ConsumeNotice)

    fun send(text: String): SendResult {
        if (text.isBlank()) return SendResult.BLANK
        val trimmed = text.trim()
        val current = _state.value
        if (trimmed.codePointCount(0, trimmed.length) > current.maxMessageLength) return SendResult.TOO_LONG
        if (current.phase != ChatPhase.Active) return SendResult.NOT_ACTIVE
        post(Input.Send(trimmed))
        return SendResult.SENT
    }

    private fun post(input: Input) {
        inbox.trySend(input) // falha só depois de Ended (caixa fechada): no-op
    }

    private fun newToken() = ++nextToken

    // ---- Ator ----

    private fun handle(input: Input) {
        if (ended) return
        when (input) {
            Input.Start -> onStart()
            Input.Background -> onBackground()
            Input.Foreground -> onForeground()
            Input.Leave -> end(EndReason.LEFT)
            Input.ConsumeNotice -> _state.update { it.copy(notice = null) }
            is Input.Send -> if (welcomed) conn?.send(encodeMessage(input.text))
            is Input.StartFix -> onStartFix(input.fix)
            is Input.Socket -> if (input.token == connToken) onSocket(input.event)
            is Input.TrackFix -> if (input.token == trackToken) onTrackFix(input.fix)
            is Input.PingTick -> if (input.token == trackToken) conn?.send(encodePing())
            is Input.BackgroundTimeout -> if (input.token == timerToken) end(EndReason.BACKGROUND_TIMEOUT)
            is Input.ForegroundFix -> if (input.token == foregroundToken) onForegroundFix(input.fix)
            is Input.ReconnectChecked -> if (input.token == reconnectToken) onReconnectChecked(input.fix)
            is Input.ReconnectDue -> if (input.token == reconnectToken) onReconnectDue(input.fix)
        }
    }

    private fun onStart() {
        if (started) return
        started = true
        if (!location.hasPermission()) return end(EndReason.NO_LOCATION_PERMISSION)
        startJob = scope.launch { post(Input.StartFix(freshFix())) }
    }

    private suspend fun freshFix(): LocationFix? =
        location.current(LocationPriority.HIGH)
            ?: location.updates(LocationPriority.HIGH, CHAT_INTERVAL_MS).firstOrNull()

    private fun onStartFix(fix: LocationFix?) {
        if (fix == null) return end(EndReason.NO_LOCATION_PERMISSION) // `updates` completou vazio
        if (fix.isMock) return end(EndReason.MOCK_LOCATION)
        updateDistance(fix.point)
        connect(fix.point)
    }

    private fun connect(point: GeoPoint) {
        val token = store.currentToken()
        if (token == null) {
            sessionEvents.notifyExpired()
            return end(EndReason.SESSION_EXPIRED)
        }
        val connection = factory.connect(landmarkId, token, point)
        val gen = newToken()
        conn = connection
        connToken = gen
        welcomed = false
        lastSentPoint = point
        connJob = scope.launch { connection.events.collect { post(Input.Socket(gen, it)) } }
    }

    private fun onSocket(event: SocketEvent) {
        when (event) {
            SocketEvent.Opened -> Unit // só o `welcome` ativa
            is SocketEvent.Text -> {
                val parsed = parseServerEvent(event.raw) ?: return
                if (parsed == ServerEvent.Pong) return
                when {
                    !inBackground -> apply(parsed)
                    parsed is ServerEvent.Welcome -> {
                        applyWelcome(parsed)
                        if (!paused) pause()
                    }
                    else -> buffer += parsed
                }
            }
            is SocketEvent.Closed -> onConnectionEnded(event.code)
            is SocketEvent.Failure -> onConnectionEnded(null)
        }
    }

    private fun apply(event: ServerEvent) {
        when (event) {
            is ServerEvent.Welcome -> {
                applyWelcome(event)
                enterActive()
            }
            is ServerEvent.Message -> {
                val key = "m:${event.id}"
                _state.update { s ->
                    if (s.items.any { it.key == key }) {
                        s
                    } else {
                        val mine = event.user.id == me?.id
                        s.copy(items = s.items + ChatItem.Msg(key, event.user.name, event.text, mine, event.sentAt))
                    }
                }
            }
            is ServerEvent.Presence -> {
                if (event.joined && event.user.id == me?.id) return
                val note = ChatItem.PresenceNote("p:${++presenceSeq}", event.user.name, event.joined)
                _state.update { it.copy(items = it.items + note, memberCount = event.memberCount ?: it.memberCount) }
            }
            is ServerEvent.Error -> _state.update { it.copy(notice = event.message.take(MAX_NOTICE_LENGTH)) }
            ServerEvent.Pong -> Unit
        }
    }

    /** Guarda dados do `welcome` da conexão atual e zera o backoff, sem mudar a fase. */
    private fun applyWelcome(event: ServerEvent.Welcome) {
        me = event.me
        welcomed = true
        attempt = 0
        _state.update {
            it.copy(
                landmarkName = event.landmarkName,
                memberCount = event.memberCount,
                joinRadiusM = event.joinRadiusM,
                leaveRadiusM = event.leaveRadiusM,
                maxMessageLength = event.maxMessageLength,
                warnLeaving = it.distanceM?.let { d -> d > event.joinRadiusM } ?: false,
            )
        }
    }

    private fun enterActive() {
        setPhase(ChatPhase.Active)
        startTracking()
    }

    private fun onConnectionEnded(code: Int?) {
        conn = null
        connToken = 0
        connJob = null
        welcomed = false
        stopTracking()
        when (code) {
            CLOSE_OUT_OF_RANGE -> end(EndReason.OUT_OF_RANGE)
            CLOSE_UNAUTHORIZED -> {
                sessionEvents.notifyExpired()
                end(EndReason.SESSION_EXPIRED)
            }
            CLOSE_NOT_FOUND, CLOSE_BAD_REQUEST -> end(EndReason.NOT_FOUND)
            // em segundo plano não reconecta: decide-se ao voltar ao app
            else -> if (inBackground) pendingReconnect = true else scheduleReconnect()
        }
    }

    // ---- Durante Active: localização e ping ----

    private fun startTracking() {
        stopTracking()
        if (inBackground) return // posição e ping só depois que a volta ao app passar na checagem de raio
        val token = newToken()
        trackToken = token
        locationJob = scope.launch {
            location.updates(LocationPriority.HIGH, CHAT_INTERVAL_MS).collect { post(Input.TrackFix(token, it)) }
        }
        pingJob = scope.launch {
            while (true) {
                delay(config.pingIntervalMs)
                post(Input.PingTick(token))
            }
        }
    }

    private fun stopTracking() {
        trackToken = 0
        locationJob?.cancel()
        pingJob?.cancel()
        locationJob = null
        pingJob = null
    }

    private fun onTrackFix(fix: LocationFix) {
        if (fix.isMock) return end(EndReason.MOCK_LOCATION)
        updateDistance(fix.point)
        val last = lastSentPoint
        if (last == null || distanceMeters(last, fix.point) >= config.minDisplacementM) sendLocation(fix.point)
    }

    private fun sendLocation(point: GeoPoint) {
        conn?.send(encodeLocation(point))
        lastSentPoint = point
    }

    /** Atualiza a distância e o alerta de afastamento; devolve a distância ao ponto. */
    private fun updateDistance(point: GeoPoint): Double {
        val d = distanceMeters(point, landmarkPoint)
        _state.update { it.copy(distanceM = d, warnLeaving = d > it.joinRadiusM) }
        return d
    }

    // ---- Reconexão ----

    private fun scheduleReconnect() {
        if (!location.hasPermission()) return end(EndReason.NO_LOCATION_PERMISSION)
        setPhase(ChatPhase.Reconnecting)
        val waitMs = config.backoffMs[minOf(attempt, config.backoffMs.lastIndex)]
        attempt++
        val token = newToken()
        reconnectToken = token
        reconnectJob = scope.launch {
            val fix = location.current(LocationPriority.HIGH)
            post(Input.ReconnectChecked(token, fix))
            delay(waitMs)
            post(Input.ReconnectDue(token, fix))
        }
    }

    private fun onReconnectChecked(fix: LocationFix?) {
        if (fix == null) return // posição desconhecida: tenta de novo depois da espera
        if (fix.isMock) return end(EndReason.MOCK_LOCATION)
        if (updateDistance(fix.point) > _state.value.joinRadiusM) end(EndReason.OUT_OF_RANGE)
    }

    private fun onReconnectDue(fix: LocationFix?) {
        reconnectToken = 0
        reconnectJob = null
        when {
            fix == null -> scheduleReconnect()
            !location.hasPermission() -> end(EndReason.NO_LOCATION_PERMISSION)
            else -> connect(fix.point)
        }
    }

    private fun cancelReconnect() {
        reconnectToken = 0
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ---- Segundo plano ----

    private fun onBackground() {
        if (inBackground) {
            // saiu de novo enquanto obtinha o fix da volta: segue em segundo plano com o timer original
            foregroundToken = 0
            foregroundJob?.cancel()
            foregroundJob = null
            return
        }
        inBackground = true
        val token = newToken()
        timerToken = token
        timerJob = scope.launch {
            delay(config.backgroundToleranceMs)
            post(Input.BackgroundTimeout(token))
        }
        val phase = _state.value.phase
        if (phase == ChatPhase.Active || phase == ChatPhase.Reconnecting) {
            if (reconnectToken != 0) cancelReconnect()
            if (conn == null) pendingReconnect = true
            pause()
        }
        // Connecting: a tentativa segue; um `welcome` em segundo plano leva a Paused
    }

    private fun pause() {
        paused = true
        stopTracking()
        setPhase(ChatPhase.Paused)
    }

    private fun onForeground() {
        if (!inBackground || foregroundToken != 0) return
        if (!location.hasPermission()) return end(EndReason.NO_LOCATION_PERMISSION)
        if (!paused) {
            // ainda em Connecting: nada foi exibido nem guardado
            inBackground = false
            cancelTimer()
            if (pendingReconnect) {
                pendingReconnect = false
                scheduleReconnect()
            }
            return
        }
        val token = newToken()
        foregroundToken = token
        foregroundJob = scope.launch { post(Input.ForegroundFix(token, freshFix())) }
    }

    private fun onForegroundFix(fix: LocationFix?) {
        foregroundToken = 0
        foregroundJob = null
        if (fix == null) return end(EndReason.NO_LOCATION_PERMISSION) // `updates` completou vazio
        cancelTimer()
        if (fix.isMock) return end(EndReason.MOCK_LOCATION)
        if (updateDistance(fix.point) > _state.value.leaveRadiusM) return end(EndReason.OUT_OF_RANGE)

        inBackground = false
        paused = false
        // a posição nova vai antes de mostrar o que foi guardado
        if (conn != null && welcomed) sendLocation(fix.point)
        val pending = buffer.toList()
        buffer.clear()
        pending.forEach(::apply)
        when {
            conn == null -> { // a conexão caiu em segundo plano: regras de reconexão
                pendingReconnect = false
                scheduleReconnect()
            }
            welcomed -> enterActive()
            else -> setPhase(ChatPhase.Reconnecting) // conexão aberta, ainda sem `welcome`
        }
    }

    private fun cancelTimer() {
        timerToken = 0
        timerJob?.cancel()
        timerJob = null
    }

    // ---- Fim ----

    private fun end(reason: EndReason) {
        ended = true
        listOf(startJob, connJob, locationJob, pingJob, reconnectJob, timerJob, foregroundJob).forEach { it?.cancel() }
        conn?.close(CLOSE_NORMAL)
        conn = null
        buffer.clear()
        _state.update { it.copy(phase = ChatPhase.Ended(reason), items = emptyList()) }
        inbox.close()
    }

    private fun setPhase(phase: ChatPhase) = _state.update { it.copy(phase = phase) }
}
