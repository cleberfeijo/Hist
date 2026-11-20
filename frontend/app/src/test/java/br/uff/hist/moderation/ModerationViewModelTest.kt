package br.uff.hist.moderation

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeModerationApi
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.curiosityDto
import br.uff.hist.testutil.httpError
import br.uff.hist.testutil.landmarkDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ModerationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeModerationApi()
    private val repository = ModerationRepository(api, ApiCaller(HistJson, SessionEvents()))
    private val messages = ErrorMessages(ApplicationProvider.getApplicationContext())

    private fun queue() = ModerationQueueViewModel(repository)

    private fun detail(kind: ModerationKind = ModerationKind.LANDMARK) =
        ModerationDetailViewModel(repository, messages, SavedStateHandle(mapOf("id" to "a"))).also { it.open(kind) }

    @Test fun `filas pedem so os pendentes`() = runTest(dispatcher) {
        api.landmarkQueue = listOf(landmarkDto("a", "pending"))
        api.curiosityQueue = listOf(curiosityDto("c", "pending"))
        val vm = queue()
        advanceUntilIdle()
        assertEquals(listOf("pending", "pending"), api.queueStatuses)
        assertEquals(listOf("a"), vm.uiState.value.landmarks.items.map { it.id })
        assertEquals(listOf("c"), vm.uiState.value.curiosities.items.map { it.id })
    }

    @Test fun `fila vazia expoe estado vazio carregado`() = runTest(dispatcher) {
        val vm = queue()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.landmarks.loaded && s.landmarks.items.isEmpty())
        assertTrue(s.curiosities.loaded && s.curiosities.items.isEmpty())
    }

    @Test fun `decidido sai da fila e deixa o aviso`() = runTest(dispatcher) {
        api.landmarkQueue = listOf(landmarkDto("a", "pending"), landmarkDto("b", "pending"))
        val vm = queue()
        advanceUntilIdle()
        vm.decided("a", "Aprovado.")
        assertEquals(listOf("b"), vm.uiState.value.landmarks.items.map { it.id })
        assertEquals("Aprovado.", vm.uiState.value.notice)
        vm.dismissNotice()
        assertNull(vm.uiState.value.notice)
    }

    @Test fun `motivo curto gera erro local e nenhuma chamada`() = runTest(dispatcher) {
        val vm = detail()
        advanceUntilIdle()
        vm.reject("ok")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reasonError)
        assertTrue(api.rejected.isEmpty())
        assertNull(vm.uiState.value.decision)
    }

    @Test fun `motivo so com espacos e invalido`() = runTest(dispatcher) {
        val vm = detail()
        advanceUntilIdle()
        vm.reject("   a   ")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reasonError)
        assertTrue(api.rejected.isEmpty())
    }

    @Test fun `motivo acima de 500 e invalido`() = runTest(dispatcher) {
        assertFalse(isValidReason("x".repeat(501)))
        assertTrue(isValidReason("x".repeat(500)))
    }

    @Test fun `motivo valido rejeita uma unica vez com o motivo aparado`() = runTest(dispatcher) {
        api.gate = CompletableDeferred()
        val vm = detail()
        advanceUntilIdle()
        vm.reject("  duplicado  ")
        vm.reject("  duplicado  ")
        advanceUntilIdle()
        assertEquals(listOf("a" to "duplicado"), api.rejected)
        assertTrue(vm.uiState.value.submitting)
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("Rejeitado.", vm.uiState.value.decision?.notice)
        assertEquals("a", vm.uiState.value.decision?.id)
    }

    @Test fun `curiosidade rejeitada usa a rota de curiosidade`() = runTest(dispatcher) {
        val vm = detail(ModerationKind.CURIOSITY)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.curiosity)
        vm.reject("fora do tema")
        advanceUntilIdle()
        assertEquals(listOf("a" to "fora do tema"), api.rejected)
    }

    @Test fun `aprovar chama a API uma vez mesmo com toque duplo`() = runTest(dispatcher) {
        api.gate = CompletableDeferred()
        val vm = detail()
        advanceUntilIdle()
        vm.approve()
        vm.approve()
        advanceUntilIdle()
        assertEquals(listOf("a"), api.approved)
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("Aprovado.", vm.uiState.value.decision?.notice)
    }

    @Test fun `not_found ao aprovar vira aviso de ja revisado sem erro`() = runTest(dispatcher) {
        api.decisionFailure = httpError(404, "not_found", "Item não encontrado")
        val vm = detail()
        advanceUntilIdle()
        vm.approve()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("Este item já foi revisado por outra pessoa.", s.decision?.notice)
        assertNull(s.error)
        assertFalse(s.submitting)
    }

    @Test fun `not_found ao rejeitar tambem vira ja revisado`() = runTest(dispatcher) {
        api.decisionFailure = httpError(404, "not_found", "x")
        val vm = detail()
        advanceUntilIdle()
        vm.reject("motivo")
        advanceUntilIdle()
        assertEquals("Este item já foi revisado por outra pessoa.", vm.uiState.value.decision?.notice)
        assertFalse(vm.uiState.value.rejectDialog)
    }

    @Test fun `outro erro mantem o dialogo e libera para tentar de novo`() = runTest(dispatcher) {
        api.decisionFailure = java.io.IOException()
        val vm = detail()
        advanceUntilIdle()
        vm.askReject()
        vm.reject("motivo")
        advanceUntilIdle()
        val s = vm.uiState.value
        assertNull(s.decision)
        assertNotNull(s.error)
        assertTrue(s.rejectDialog)
        assertFalse(s.submitting)
    }

    @Test fun `motivo digitado fica no estado`() = runTest(dispatcher) {
        val vm = detail()
        advanceUntilIdle()
        vm.askReject()
        vm.setReason("abc")
        assertEquals("abc", vm.uiState.value.reason)
        assertTrue(vm.uiState.value.rejectDialog)
    }

    @Test fun `falha ao carregar o detalhe permite tentar de novo`() = runTest(dispatcher) {
        api.detailFailure = java.io.IOException()
        val vm = detail()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadFailed)
        api.detailFailure = null
        vm.retry()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.landmark)
        assertFalse(vm.uiState.value.loadFailed)
    }
}
