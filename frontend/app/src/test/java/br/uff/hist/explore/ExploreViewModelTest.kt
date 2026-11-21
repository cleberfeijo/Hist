package br.uff.hist.explore

import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.RIO_DAS_OSTRAS
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeLocationProvider
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.unsupportedApi
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private data class NearbyCall(val lat: Double, val lon: Double, val radius: Int, val limit: Int)

private class FakeNearbyApi : HistApi by unsupportedApi() {
    val calls = mutableListOf<NearbyCall>()
    var handler: (NearbyCall) -> List<LandmarkDto> = { emptyList() }

    override suspend fun nearby(lat: Double, lon: Double, radius: Int, limit: Int): List<LandmarkDto> {
        val call = NearbyCall(lat, lon, radius, limit)
        calls += call
        return handler(call)
    }
}

private fun dto(id: String, lat: Double, lon: Double) =
    LandmarkDto(id = id, name = "Ponto $id", latitude = lat, longitude = lon, status = "approved")

private val USER = GeoPoint(-22.5269, -41.9450)

// ~87 m, ~410 m, 10 m e 30 m ao norte do usuário (1 grau de latitude = 111,2 km).
private fun north(meters: Double) = GeoPoint(USER.latitude + meters / 111_195.0, USER.longitude)

private fun fix(p: GeoPoint) = LocationFix(p, 5f, false)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeNearbyApi()
    private val location = FakeLocationProvider()

    private fun vm() = ExploreViewModel(
        LandmarkRepository(api, ApiCaller(HistJson, SessionEvents())),
        location,
    )

    private fun TestScope.started(vm: ExploreViewModel): ExploreViewModel {
        vm.onStart()
        advanceUntilIdle()
        return vm
    }

    @Test fun `primeira posicao busca 1 km ao redor do usuario e titula Perto de voce`() = runTest(dispatcher) {
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(listOf(NearbyCall(USER.latitude, USER.longitude, 1000, 200)), api.calls)
        assertEquals(SheetTitle.NEAR_YOU, vm.uiState.value.sheetTitle)
        assertEquals(USER, vm.uiState.value.user)
    }

    @Test fun `com permissao o titulo e Perto de voce antes de qualquer posicao`() = runTest(dispatcher) {
        val vm = started(vm())
        assertEquals(SheetTitle.NEAR_YOU, vm.uiState.value.sheetTitle)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `10 m nao refaz a busca e 30 m refaz`() = runTest(dispatcher) {
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        location.emit(fix(north(10.0)))
        advanceUntilIdle()
        assertEquals(1, api.calls.size)
        location.emit(fix(north(30.0)))
        advanceUntilIdle()
        assertEquals(2, api.calls.size)
        assertEquals(north(30.0).latitude, api.calls[1].lat, 1e-9)
        assertEquals(north(30.0), vm.uiState.value.user)
    }

    @Test fun `itens usam a distancia do usuario, ordenam e liberam o chat ate 200 m`() = runTest(dispatcher) {
        val far = north(410.0)
        val near = north(87.0)
        api.handler = { listOf(dto("longe", far.latitude, far.longitude), dto("perto", near.latitude, near.longitude)) }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        val items = vm.uiState.value.sheetItems
        assertEquals(listOf("perto", "longe"), items.map { it.landmark.id })
        assertEquals(87.0, items[0].distanceM!!, 1.5)
        assertTrue(items[0].chatAvailable)
        assertEquals(410.0, items[1].distanceM!!, 1.5)
        assertFalse(items[1].chatAvailable)
    }

    @Test fun `so localizacao aproximada nao libera chat e sinaliza precisao`() = runTest(dispatcher) {
        location.setPrecise(false)
        val near = north(87.0)
        api.handler = { listOf(dto("perto", near.latitude, near.longitude)) }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasPermission)
        assertFalse(vm.uiState.value.hasPreciseLocation)
        assertFalse(vm.uiState.value.sheetItems.single().chatAvailable)
        location.setPrecise(true)
        vm.refreshPermission()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasPreciseLocation)
        assertTrue(vm.uiState.value.sheetItems.single().chatAvailable)
    }

    @Test fun `sheet so conta como carregado depois da primeira busca ao redor do usuario`() = runTest(dispatcher) {
        val vm = started(vm())
        assertFalse(vm.uiState.value.sheetLoaded) // com permissao, ainda sem posicao
        location.emit(fix(USER))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sheetLoaded)
        assertTrue(vm.uiState.value.sheetItems.isEmpty())
    }

    @Test fun `sem permissao o sheet carrega com a busca da camera`() = runTest(dispatcher) {
        location.setPermission(false)
        val vm = started(vm())
        assertFalse(vm.uiState.value.sheetLoaded)
        vm.onCameraIdle(USER, 2000.0)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sheetLoaded)
    }

    @Test fun `centralizar no usuario fica guardado no estado`() = runTest(dispatcher) {
        val vm = started(vm())
        assertFalse(vm.uiState.value.centeredOnUser)
        vm.onCenteredOnUser()
        assertTrue(vm.uiState.value.centeredOnUser)
    }

    @Test fun `onCameraIdle limita o raio a 50 km e nao altera o sheet`() = runTest(dispatcher) {
        val near = north(87.0)
        api.handler = { c ->
            if (c.radius == 1000) listOf(dto("perto", near.latitude, near.longitude)) else listOf(dto("cam", -22.0, -41.5))
        }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        val sheetBefore = vm.uiState.value.sheetItems
        val center = GeoPoint(USER.latitude + 0.36, USER.longitude)
        vm.onCameraIdle(center, 80_000.0)
        advanceUntilIdle()
        assertEquals(NearbyCall(center.latitude, center.longitude, 50_000, 200), api.calls.last())
        assertEquals(listOf("cam"), vm.uiState.value.markers.map { it.landmark.id })
        assertEquals(sheetBefore, vm.uiState.value.sheetItems)
        assertEquals(listOf("perto"), vm.uiState.value.sheetItems.map { it.landmark.id })
    }

    @Test fun `raio visivel e arredondado para cima`() = runTest(dispatcher) {
        val vm = started(vm())
        vm.onCameraIdle(USER, 1234.2)
        advanceUntilIdle()
        assertEquals(1235, api.calls.last().radius)
    }

    @Test fun `marcadores recalculam a distancia a partir do usuario`() = runTest(dispatcher) {
        val p = north(87.0)
        api.handler = { c -> if (c.radius == 1000) emptyList() else listOf(dto("m", p.latitude, p.longitude)) }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        vm.onCameraIdle(GeoPoint(-22.0, -41.0), 5000.0)
        advanceUntilIdle()
        val marker = vm.uiState.value.markers.single()
        assertEquals(87.0, marker.distanceM!!, 1.5)
        assertTrue(marker.chatAvailable)
    }

    @Test fun `sem permissao e sem ultima posicao centra em Rio das Ostras e lista a area da camera`() = runTest(dispatcher) {
        location.setPermission(false)
        val p = north(87.0)
        api.handler = { listOf(dto("a", p.latitude, p.longitude)) }
        val vm = started(vm())
        assertEquals(GeoPoint(-22.5269, -41.9450), vm.uiState.value.initialCamera)
        assertEquals(RIO_DAS_OSTRAS, vm.uiState.value.initialCamera)
        assertFalse(vm.uiState.value.hasPermission)
        assertEquals(SheetTitle.THIS_AREA, vm.uiState.value.sheetTitle)
        vm.onCameraIdle(USER, 2000.0)
        advanceUntilIdle()
        val items = vm.uiState.value.sheetItems
        assertEquals(listOf("a"), items.map { it.landmark.id })
        assertTrue(items.all { !it.chatAvailable && it.distanceM == null })
        assertEquals(1, api.calls.size)
    }

    @Test fun `sem permissao usa a ultima posicao conhecida`() = runTest(dispatcher) {
        location.lastKnownResult = fix(north(500.0))
        val vm = started(vm())
        assertEquals(north(500.0), vm.uiState.value.initialCamera)
    }

    @Test fun `conceder a permissao passa a coletar posicoes`() = runTest(dispatcher) {
        location.setPermission(false)
        val vm = started(vm())
        location.setPermission(true)
        vm.onPermissionResult(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasPermission)
        assertEquals(SheetTitle.NEAR_YOU, vm.uiState.value.sheetTitle)
        location.emit(fix(USER))
        advanceUntilIdle()
        assertEquals(1, api.calls.size)
    }

    @Test fun `refreshPermission detecta permissao dada nos ajustes`() = runTest(dispatcher) {
        location.setPermission(false)
        val vm = started(vm())
        assertFalse(vm.uiState.value.hasPermission)
        location.setPermission(true)
        vm.refreshPermission()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasPermission)
    }

    @Test fun `onStop para de coletar posicoes`() = runTest(dispatcher) {
        val vm = started(vm())
        vm.onStop()
        advanceUntilIdle()
        location.emit(fix(USER))
        advanceUntilIdle()
        assertTrue(api.calls.isEmpty())
        assertNull(vm.uiState.value.user)
    }

    @Test fun `falha de rede encerra o loading, mostra o erro e retry repete e limpa`() = runTest(dispatcher) {
        var fail = true
        val p = north(87.0)
        api.handler = { if (fail) throw IOException("offline") else listOf(dto("a", p.latitude, p.longitude)) }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(AppError.Network, vm.uiState.value.error)
        fail = false
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, api.calls.size)
        assertEquals(USER.latitude, api.calls[1].lat, 1e-9)
        assertNull(vm.uiState.value.error)
        assertEquals(listOf("a"), vm.uiState.value.sheetItems.map { it.landmark.id })
    }

    @Test fun `falha da busca da camera nao apaga o sheet nem o inverso`() = runTest(dispatcher) {
        val p = north(87.0)
        api.handler = { c -> if (c.radius == 1000) listOf(dto("perto", p.latitude, p.longitude)) else throw IOException() }
        val vm = started(vm())
        location.emit(fix(USER))
        advanceUntilIdle()
        vm.onCameraIdle(GeoPoint(-22.0, -41.0), 3000.0)
        advanceUntilIdle()
        assertEquals(AppError.Network, vm.uiState.value.error)
        assertEquals(listOf("perto"), vm.uiState.value.sheetItems.map { it.landmark.id })
    }

    @Test fun `mensagem de erro em pt-BR vem do ErrorMessages`() {
        val messages = ErrorMessages(ApplicationProvider.getApplicationContext())
        assertEquals("Sem conexão. Verifique sua internet.", messages.of(AppError.Network))
    }
}
