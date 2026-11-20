package br.uff.hist.profile

import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.model.ModerationStatus
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.PageDto
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeAccountApi
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.curiosityDto
import br.uff.hist.testutil.landmarkDto
import java.io.IOException
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
class MyContributionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeAccountApi()

    private fun vm() = MyContributionsViewModel(
        AccountRepository(api, ApiCaller(HistJson, SessionEvents()), FakeSessionStore()),
        ErrorMessages(ApplicationProvider.getApplicationContext()),
    )

    @Test fun `ponto rejeitado expoe o motivo`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(1 to PageDto(listOf(landmarkDto("a", "rejected", "duplicado")), 1, 1, 20))
        val vm = vm()
        advanceUntilIdle()
        val item = vm.uiState.value.landmarks.items.single()
        assertEquals(ModerationStatus.REJECTED, item.status)
        assertEquals("duplicado", item.rejectionReason)
    }

    @Test fun `ponto aprovado nao oferece excluir e pendente oferece`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(
            1 to PageDto(listOf(landmarkDto("ok", "approved"), landmarkDto("pend", "pending")), 2, 1, 20),
        )
        val vm = vm()
        advanceUntilIdle()
        val (approved, pending) = vm.uiState.value.landmarks.items
        assertFalse(approved.canDelete())
        assertTrue(pending.canDelete())
        vm.askDelete(DeleteTarget.OfLandmark(approved.id))
        assertNull(vm.uiState.value.deleteTarget)
    }

    @Test fun `confirmar a exclusao de ponto pendente chama a API e remove da lista`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(
            1 to PageDto(listOf(landmarkDto("pend", "pending"), landmarkDto("ok", "approved")), 2, 1, 20),
        )
        val vm = vm()
        advanceUntilIdle()
        vm.askDelete(DeleteTarget.OfLandmark("pend"))
        assertNotNull(vm.uiState.value.deleteTarget)
        assertTrue(api.deletedLandmarks.isEmpty())
        vm.confirmDelete()
        vm.confirmDelete() // toque duplo: uma só chamada
        advanceUntilIdle()
        assertEquals(listOf("pend"), api.deletedLandmarks)
        assertEquals(listOf("ok"), vm.uiState.value.landmarks.items.map { it.id })
        assertNull(vm.uiState.value.deleteTarget)
    }

    @Test fun `qualquer curiosidade propria pode ser excluida`() = runTest(dispatcher) {
        api.curiosityPages = mapOf(1 to PageDto(listOf(curiosityDto("c1", "approved")), 1, 1, 20))
        val vm = vm()
        advanceUntilIdle()
        vm.askDelete(DeleteTarget.OfCuriosity("c1"))
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(listOf("c1"), api.deletedCuriosities)
        assertTrue(vm.uiState.value.curiosities.items.isEmpty())
    }

    @Test fun `falha ao excluir mantem o item e mostra o erro`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(1 to PageDto(listOf(landmarkDto("pend", "pending")), 1, 1, 20))
        api.deleteFailure = IOException()
        val vm = vm()
        advanceUntilIdle()
        vm.askDelete(DeleteTarget.OfLandmark("pend"))
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.landmarks.items.size)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.deleting)
    }

    @Test fun `paginacao anexa sem duplicar e cada aba e independente`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(
            1 to PageDto(listOf(landmarkDto("a"), landmarkDto("b")), 3, 1, 2),
            2 to PageDto(listOf(landmarkDto("b"), landmarkDto("c")), 3, 2, 2),
        )
        val vm = vm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.landmarks.hasMore)
        vm.loadMoreLandmarks()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.landmarks.items.map { it.id })
        assertFalse(vm.uiState.value.landmarks.hasMore)
        vm.loadMoreLandmarks()
        advanceUntilIdle()
        assertEquals(listOf(1, 2), api.landmarkPageCalls)
        assertEquals(listOf(1), api.curiosityPageCalls)
    }

    @Test fun `pagina so com duplicados ainda avanca para a proxima`() = runTest(dispatcher) {
        api.landmarkPages = mapOf(
            1 to PageDto(listOf(landmarkDto("a"), landmarkDto("b")), 6, 1, 2),
            2 to PageDto(listOf(landmarkDto("a"), landmarkDto("b")), 6, 2, 2),
            3 to PageDto(listOf(landmarkDto("c")), 6, 3, 2),
        )
        val vm = vm()
        advanceUntilIdle()
        val before = vm.uiState.value.landmarks.pages
        vm.loadMoreLandmarks()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.landmarks.items.size)
        assertTrue(vm.uiState.value.landmarks.pages > before) // sinaliza a tela a pedir a proxima
        vm.loadMoreLandmarks()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), api.landmarkPageCalls)
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.landmarks.items.map { it.id })
    }

    @Test fun `falha na carga marca failed e retry recarrega`() = runTest(dispatcher) {
        api.landmarkPages = emptyMap() // getValue lanca NoSuchElementException -> Unknown
        val vm = vm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.landmarks.failed)
        api.landmarkPages = mapOf(1 to PageDto(listOf(landmarkDto("a")), 1, 1, 20))
        vm.loadMoreLandmarks()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.landmarks.failed)
        assertEquals(1, vm.uiState.value.landmarks.items.size)
    }
}
