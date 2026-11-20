package br.uff.hist.contribute

import androidx.test.core.app.ApplicationProvider
import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.model.Category
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.RIO_DAS_OSTRAS
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.network.NewLandmarkRequest
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.explore.LandmarkRepository
import br.uff.hist.testutil.FakeLocationProvider
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.httpError
import br.uff.hist.testutil.unsupportedApi
import kotlinx.coroutines.CompletableDeferred
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

private class FakeSuggestApi : HistApi by unsupportedApi() {
    var calls = 0
    var last: NewLandmarkRequest? = null
    var handler: suspend (NewLandmarkRequest) -> LandmarkDto = {
        LandmarkDto(id = "n1", name = it.name, latitude = it.latitude, longitude = it.longitude, status = "pending")
    }

    override suspend fun createLandmark(body: NewLandmarkRequest): LandmarkDto {
        calls++
        last = body
        return handler(body)
    }
}

@RunWith(RobolectricTestRunner::class)
class SuggestLandmarkViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeSuggestApi()
    private val location = FakeLocationProvider()
    private fun vm() = SuggestLandmarkViewModel(
        LandmarkRepository(api, ApiCaller(HistJson, SessionEvents())),
        location,
        ErrorMessages(ApplicationProvider.getApplicationContext()),
    )

    private fun SuggestLandmarkViewModel.fill(category: Category? = Category.EDIFICIO_HISTORICO) {
        onNameChange("Casa de Cultura")
        if (category != null) onCategoryChange(category)
    }

    @Test fun `pino inicial vem da posicao atual`() {
        location.currentResult = LocationFix(GeoPoint(-22.0, -41.0), 5f, false)
        assertEquals(GeoPoint(-22.0, -41.0), vm().uiState.value.pin)
    }

    @Test fun `sem posicao atual usa a ultima conhecida`() {
        location.lastKnownResult = LocationFix(GeoPoint(-22.1, -41.1), 5f, false)
        assertEquals(GeoPoint(-22.1, -41.1), vm().uiState.value.pin)
    }

    @Test fun `sem permissao nem posicao cai no Rio das Ostras sem chamar current`() {
        location.setPermission(false)
        val v = vm()
        assertEquals(RIO_DAS_OSTRAS, v.uiState.value.pin)
        assertEquals(0, location.currentCalls)
    }

    @Test fun `nome curto da erro local sem chamada`() = runTest {
        val v = vm()
        v.fill()
        v.onNameChange("A")
        v.submit()
        assertNotNull(v.uiState.value.fieldErrors["name"])
        assertEquals(0, api.calls)
    }

    @Test fun `sem categoria da erro category`() = runTest {
        val v = vm()
        v.fill(category = null)
        v.submit()
        assertNotNull(v.uiState.value.fieldErrors["category"])
        assertEquals(0, api.calls)
    }

    @Test fun `curiosidade curta da erro curiosity`() = runTest {
        val v = vm()
        v.fill()
        v.onCuriosityChange("curta")
        v.submit()
        assertNotNull(v.uiState.value.fieldErrors["curiosity"])
        assertEquals(0, api.calls)
    }

    @Test fun `descricao acima de 5000 da erro description`() = runTest {
        val v = vm()
        v.fill()
        v.onDescriptionChange("x".repeat(5001))
        v.submit()
        assertNotNull(v.uiState.value.fieldErrors["description"])
    }

    @Test fun `envio valido manda categoria e coordenadas da camera`() = runTest {
        val v = vm()
        v.fill()
        v.onCameraMoved(GeoPoint(-22.6, -41.8))
        v.onDescriptionChange("   ")
        v.submit()
        val req = api.last!!
        assertEquals("edificio_historico", req.category)
        assertEquals(-22.6, req.latitude, 0.0)
        assertEquals(-41.8, req.longitude, 0.0)
        assertEquals("Casa de Cultura", req.name)
        assertNull(req.description)
        assertNull(req.curiosity)
        assertTrue(v.uiState.value.done)
        assertFalse(v.uiState.value.submitting)
    }

    @Test fun `submit duas vezes envia uma requisicao`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.handler = { gate.await(); LandmarkDto(id = "n1", name = "x", latitude = 0.0, longitude = 0.0, status = "pending") }
        val v = vm()
        v.fill()
        v.submit()
        assertTrue(v.uiState.value.submitting)
        v.submit()
        gate.complete(Unit)
        assertEquals(1, api.calls)
        assertTrue(v.uiState.value.done)
    }

    @Test fun `422 com details vira fieldErrors`() = runTest {
        api.handler = {
            throw httpError(
                422, "validation_error", "Dados inválidos.",
                """[{"field":"name","message":"nome ruim"},{"field":"category","message":"fora da lista"}]""",
            )
        }
        val v = vm()
        v.fill()
        v.submit()
        assertEquals(mapOf("name" to "nome ruim", "category" to "fora da lista"), v.uiState.value.fieldErrors)
        assertNull(v.uiState.value.error)
        assertFalse(v.uiState.value.submitting)
        assertFalse(v.uiState.value.done)
    }

    @Test fun `422 de latitude vira erro geral`() = runTest {
        api.handler = {
            throw httpError(422, "validation_error", "Dados inválidos.", """[{"field":"latitude","message":"fora do intervalo"}]""")
        }
        val v = vm()
        v.fill()
        v.submit()
        assertEquals("fora do intervalo", v.uiState.value.error)
        assertTrue(v.uiState.value.fieldErrors.isEmpty())
    }
}
