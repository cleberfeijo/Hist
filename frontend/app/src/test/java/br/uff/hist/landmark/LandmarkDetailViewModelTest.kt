package br.uff.hist.landmark

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.CuriosityDto
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.network.NewCuriosityRequest
import br.uff.hist.core.network.PageDto
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.explore.LandmarkRepository
import br.uff.hist.testutil.FakeLocationProvider
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.unsupportedApi
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

private val USER = GeoPoint(-22.5269, -41.9450)

private fun north(meters: Double) = GeoPoint(USER.latitude + meters / 111_195.0, USER.longitude)

private fun fix(p: GeoPoint, mock: Boolean = false) = LocationFix(p, 5f, mock)

private fun curiosityDto(id: String) = CuriosityDto(
    id = id, landmarkId = "p1", text = "Curiosidade $id", status = "approved", createdAt = "2026-01-01T10:00:00Z",
)

private class FakeDetailApi : HistApi by unsupportedApi() {
    var landmarkAt: GeoPoint = north(87.0)
    var landmarkFailure: Throwable? = null
    var pages: Map<Int, PageDto<CuriosityDto>> = mapOf(1 to PageDto(emptyList(), 0, 1, 20))
    var pageFailure: Throwable? = null
    val pageCalls = mutableListOf<Int>()
    val suggestions = mutableListOf<String>()
    var suggestGate: CompletableDeferred<Unit>? = null
    var suggestFailure: Throwable? = null
    var landmarkCalls = 0

    override suspend fun landmark(id: String): LandmarkDto {
        landmarkCalls++
        landmarkFailure?.let { throw it }
        return LandmarkDto(id = id, name = "Igreja Matriz", latitude = landmarkAt.latitude, longitude = landmarkAt.longitude, status = "approved")
    }

    override suspend fun landmarkCuriosities(id: String, page: Int, pageSize: Int): PageDto<CuriosityDto> {
        pageCalls += page
        pageFailure?.let { throw it }
        return pages.getValue(page)
    }

    override suspend fun createCuriosity(id: String, body: NewCuriosityRequest): CuriosityDto {
        suggestions += body.text
        suggestGate?.await()
        suggestFailure?.let { throw it }
        return curiosityDto("novo")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LandmarkDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeDetailApi()
    private val location = FakeLocationProvider()

    private fun vm() = LandmarkDetailViewModel(
        SavedStateHandle(mapOf("id" to "p1")),
        LandmarkRepository(api, ApiCaller(HistJson, SessionEvents())),
        location,
        ErrorMessages(ApplicationProvider.getApplicationContext()),
    )

    private fun TestScope.started(vm: LandmarkDetailViewModel = vm()): LandmarkDetailViewModel {
        vm.onStart()
        advanceUntilIdle()
        return vm
    }

    @Test fun `ponto a 87 m com permissao e Available`() = runTest(dispatcher) {
        val vm = started()
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(ChatAccess.Available, vm.uiState.value.chatAccess)
        assertEquals(87.0, vm.uiState.value.distanceM!!, 1.0)
        assertEquals("Igreja Matriz", vm.uiState.value.landmark?.name)
    }

    @Test fun `ponto a 410 m e TooFar com 210 faltando`() = runTest(dispatcher) {
        api.landmarkAt = north(410.0)
        val vm = started()
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(ChatAccess.TooFar(210), vm.uiState.value.chatAccess)
    }

    @Test fun `fix simulado a 50 m e MockLocation`() = runTest(dispatcher) {
        api.landmarkAt = north(50.0)
        val vm = started()
        location.emit(fix(USER, mock = true))
        advanceUntilIdle()
        assertEquals(ChatAccess.MockLocation, vm.uiState.value.chatAccess)
    }

    @Test fun `sem permissao e NoPermission`() = runTest(dispatcher) {
        location.setPermission(false)
        val vm = started()
        assertEquals(ChatAccess.NoPermission, vm.uiState.value.chatAccess)
    }

    @Test fun `permissao concedida depois passa de NoPermission para Locating`() = runTest(dispatcher) {
        location.setPermission(false)
        val vm = started()
        location.setPermission(true)
        vm.onPermissionResult(true)
        advanceUntilIdle()
        assertEquals(ChatAccess.Locating, vm.uiState.value.chatAccess)
    }

    @Test fun `com permissao e sem nenhum fix fica Locating`() = runTest(dispatcher) {
        val vm = started()
        assertEquals(ChatAccess.Locating, vm.uiState.value.chatAccess)
        assertNull(vm.uiState.value.distanceM)
    }

    @Test fun `falha ao carregar o ponto expoe o erro e retry recarrega`() = runTest(dispatcher) {
        api.landmarkFailure = IOException("offline")
        val vm = started()
        assertEquals(AppError.Network, vm.uiState.value.error)
        assertNull(vm.uiState.value.landmark)
        api.landmarkFailure = null
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.landmark)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun `sugestao curta gera erro local sem chamar a API`() = runTest(dispatcher) {
        val vm = started()
        vm.openSuggestion()
        vm.onSuggestionText("curta")
        vm.submitSuggestion()
        advanceUntilIdle()
        assertTrue(api.suggestions.isEmpty())
        assertEquals("A curiosidade precisa ter entre 10 e 2000 caracteres.", vm.uiState.value.suggestion.error)
        assertFalse(vm.uiState.value.suggestion.sent)
    }

    @Test fun `sugestao valida envia uma vez e marca sent`() = runTest(dispatcher) {
        val vm = started()
        vm.openSuggestion()
        vm.onSuggestionText("  Esta igreja foi fundada em 1750.  ")
        vm.submitSuggestion()
        advanceUntilIdle()
        assertEquals(listOf("Esta igreja foi fundada em 1750."), api.suggestions)
        val s = vm.uiState.value.suggestion
        assertTrue(s.sent)
        assertFalse(s.open)
        assertFalse(s.submitting)
        assertNull(s.error)
    }

    @Test fun `submitSuggestion duas vezes seguidas faz uma requisicao`() = runTest(dispatcher) {
        api.suggestGate = CompletableDeferred()
        val vm = started()
        vm.openSuggestion()
        vm.onSuggestionText("Texto com mais de dez caracteres")
        vm.submitSuggestion()
        vm.submitSuggestion()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestion.submitting)
        api.suggestGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, api.suggestions.size)
        assertTrue(vm.uiState.value.suggestion.sent)
    }

    @Test fun `erro do servidor na sugestao mostra a mensagem e reabilita o envio`() = runTest(dispatcher) {
        api.suggestFailure = IOException("offline")
        val vm = started()
        vm.openSuggestion()
        vm.onSuggestionText("Texto com mais de dez caracteres")
        vm.submitSuggestion()
        advanceUntilIdle()
        val s = vm.uiState.value.suggestion
        assertEquals("Sem conexão. Verifique sua internet.", s.error)
        assertFalse(s.submitting)
        assertTrue(s.open)
        assertFalse(s.sent)
    }

    @Test fun `loadMore sem mais paginas nao chama a API e a pagina 2 e anexada`() = runTest(dispatcher) {
        api.pages = mapOf(
            1 to PageDto(listOf(curiosityDto("a")), 2, 1, 1),
            2 to PageDto(listOf(curiosityDto("b")), 2, 2, 1),
        )
        val vm = started()
        assertTrue(vm.uiState.value.hasMore)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2), api.pageCalls)
        assertEquals(listOf("a", "b"), vm.uiState.value.curiosities.map { it.id })
        assertFalse(vm.uiState.value.hasMore)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2), api.pageCalls)
    }

    @Test fun `falha na pagina 2 mantem os itens e retry tenta a mesma pagina`() = runTest(dispatcher) {
        api.pages = mapOf(
            1 to PageDto(listOf(curiosityDto("a")), 2, 1, 1),
            2 to PageDto(listOf(curiosityDto("b")), 2, 2, 1),
        )
        val vm = started()
        api.pageFailure = IOException("offline")
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.curiosities.map { it.id })
        assertTrue(vm.uiState.value.curiositiesFailed)
        assertNull(vm.uiState.value.error)
        api.pageFailure = null
        vm.retry()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.uiState.value.curiosities.map { it.id })
        assertFalse(vm.uiState.value.curiositiesFailed)
        assertEquals(1, api.landmarkCalls)
    }

    @Test fun `apos onStop e onStart volta a Locating ate um fix novo`() = runTest(dispatcher) {
        val vm = started()
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(ChatAccess.Available, vm.uiState.value.chatAccess)
        vm.onStop()
        vm.onStart()
        advanceUntilIdle()
        assertEquals(ChatAccess.Locating, vm.uiState.value.chatAccess)
        assertNull(vm.uiState.value.distanceM)
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(ChatAccess.Available, vm.uiState.value.chatAccess)
    }

    @Test fun `pagina 2 com item repetido nao duplica ids`() = runTest(dispatcher) {
        api.pages = mapOf(
            1 to PageDto(listOf(curiosityDto("a"), curiosityDto("b")), 4, 1, 2),
            2 to PageDto(listOf(curiosityDto("b"), curiosityDto("c")), 4, 2, 2),
        )
        val vm = started()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.curiosities.map { it.id })
    }
}
