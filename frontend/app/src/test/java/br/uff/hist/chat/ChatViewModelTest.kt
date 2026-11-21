package br.uff.hist.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import br.uff.hist.R
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.explore.LandmarkRepository
import br.uff.hist.testutil.FakeChatSocketFactory
import br.uff.hist.testutil.FakeLocationProvider
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.unsupportedApi
import java.io.IOException
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val POINT = GeoPoint(-22.5269, -41.9450)

private const val WELCOME = """{"type":"welcome","room":"p1","landmark":{"id":"p1","name":"Igreja"},
    "user":{"id":"u1","name":"Ana"},"join_radius_m":200,"leave_radius_m":300,"max_message_length":1000}"""

private class FakeLandmarkApi : HistApi by unsupportedApi() {
    var failure: Throwable? = null
    var calls = 0

    override suspend fun landmark(id: String): LandmarkDto {
        calls++
        failure?.let { throw it }
        return LandmarkDto(id = id, name = "Igreja Matriz", latitude = POINT.latitude, longitude = POINT.longitude, status = "approved")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeLandmarkApi()
    private val sockets = FakeChatSocketFactory()
    private val location = FakeLocationProvider().apply { currentResult = LocationFix(POINT, 5f, false) }
    private val signal = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    private val events = SessionEvents()

    private fun vm(foreground: Flow<Boolean> = signal): ChatViewModel {
        val store = FakeSessionStore(Session("tok", User("u1", "Ana", "Souza", "ana@x.com", setOf(Role.USER))))
        return ChatViewModel(
            SavedStateHandle(mapOf("landmarkId" to "p1")),
            LandmarkRepository(api, ApiCaller(HistJson, events)),
            ChatSessionFactory(sockets, location, store, events),
            { foreground },
            ErrorMessages(ApplicationProvider.getApplicationContext()),
        )
    }

    /** Libera o ViewModel pelo caminho real (onCleared); sem isso o ping infinito da sessão impede o runTest de terminar. */
    private fun clear(vm: ChatViewModel) {
        val store = ViewModelStore()
        ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = vm as T
            },
        )[ChatViewModel::class]
        store.clear()
    }

    private fun vmTest(
        foreground: Flow<Boolean> = signal,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(dispatcher, timeout = 30.seconds) {
        val vm = vm(foreground)
        try {
            body(vm)
        } finally {
            clear(vm)
        }
    }

    private fun ChatViewModel.chat(): ChatState = (uiState.value as ChatScreenState.Ready).chat

    private fun TestScopeActive(vm: ChatViewModel, scope: TestScope) {
        scope.runCurrent()
        sockets.lastConnection!!.serverSend(WELCOME)
        scope.runCurrent()
        assertEquals(ChatPhase.Active, vm.chat().phase)
    }

    @Test fun `sinal de segundo plano pausa a sessao e o retorno reativa`() = vmTest { vm ->
        TestScopeActive(vm, this)
        assertEquals("Igreja", vm.chat().landmarkName)

        signal.emit(false)
        runCurrent()
        assertEquals(ChatPhase.Paused, vm.chat().phase)

        signal.emit(true)
        runCurrent()
        assertEquals(ChatPhase.Active, vm.chat().phase)
    }

    @Test fun `titulo usa o nome do ponto carregado antes do welcome e o welcome sobrescreve`() = vmTest { vm ->
        runCurrent()
        assertEquals(ChatPhase.Connecting, vm.chat().phase)
        assertEquals("Igreja Matriz", vm.chat().landmarkName)

        sockets.lastConnection!!.serverSend(WELCOME)
        runCurrent()
        assertEquals("Igreja", vm.chat().landmarkName)
    }

    @Test fun `replay inicial de primeiro plano nao dispara onAppForeground`() =
        vmTest(foreground = flow { emit(true); emitAll(signal) }) { vm ->
            TestScopeActive(vm, this)
            val fixes = location.currentCalls

            runCurrent()
            assertEquals(ChatPhase.Active, vm.chat().phase)
            assertEquals(fixes, location.currentCalls)
            assertEquals(1, sockets.connectCount)

            // Uma transição real depois do replay continua funcionando.
            signal.emit(false)
            runCurrent()
            assertEquals(ChatPhase.Paused, vm.chat().phase)
        }

    @Test fun `onCleared sai do chat e fecha a conexao`() = vmTest { vm ->
        TestScopeActive(vm, this)
        clear(vm)
        runCurrent()
        assertEquals(1000, sockets.lastConnection!!.closedByClient)
    }

    @Test fun `falha ao carregar o ponto mostra erro sem conectar e tentar de novo recupera`() = vmTest { vm ->
        api.failure = IOException("sem rede")
        runCurrent()
        assertTrue(vm.uiState.value is ChatScreenState.Failed)
        assertEquals(0, sockets.connectCount)

        api.failure = null
        vm.retry()
        runCurrent()
        assertTrue(vm.uiState.value is ChatScreenState.Ready)
        assertEquals(1, sockets.connectCount)
        assertEquals("p1", sockets.connects.single().landmarkId)
        assertEquals(2, api.calls)
    }

    @Test fun `endMessage aponta para as strings da spec`() {
        assertEquals(R.string.chat_out_of_range, endMessage(EndReason.OUT_OF_RANGE))
        assertEquals(R.string.chat_background_timeout, endMessage(EndReason.BACKGROUND_TIMEOUT))
        assertEquals(R.string.chat_mock_location, endMessage(EndReason.MOCK_LOCATION))
        assertEquals(R.string.chat_not_found, endMessage(EndReason.NOT_FOUND))
        assertEquals(R.string.chat_no_permission, endMessage(EndReason.NO_LOCATION_PERMISSION))
        assertEquals(R.string.chat_needs_precise, endMessage(EndReason.NO_PRECISE_LOCATION))
        assertEquals(R.string.chat_too_far_to_join, endMessage(EndReason.TOO_FAR_TO_JOIN))
        assertNull(endMessage(EndReason.SESSION_EXPIRED))
        assertNull(endMessage(EndReason.LEFT))
    }
}
