package br.uff.hist.explore

import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.NEARBY_LIMIT
import br.uff.hist.core.model.Page
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.NewCuriosityRequest
import br.uff.hist.core.network.NewLandmarkRequest
import br.uff.hist.core.network.map
import br.uff.hist.core.network.toDomain
import javax.inject.Inject

private const val CURIOSITIES_PAGE_SIZE = 20

/**
 * O `distance_m` do servidor é ignorado de propósito: ele se refere ao ponto consultado (centro da câmera),
 * não ao usuário. A distância é recalculada no aparelho (spec 5.3).
 */
class LandmarkRepository @Inject constructor(
    private val api: HistApi,
    private val caller: ApiCaller,
) {
    suspend fun nearby(center: GeoPoint, radiusM: Int, limit: Int = NEARBY_LIMIT): ApiResult<List<Landmark>> =
        caller.call { api.nearby(center.latitude, center.longitude, radiusM, limit) }
            .map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): ApiResult<Landmark> =
        caller.call { api.landmark(id) }.map { it.toDomain() }

    suspend fun curiosities(id: String, page: Int): ApiResult<Page<Curiosity>> =
        caller.call { api.landmarkCuriosities(id, page, CURIOSITIES_PAGE_SIZE) }
            .map { dto -> dto.toDomain { it.toDomain() } }

    suspend fun suggestCuriosity(id: String, text: String): ApiResult<Curiosity> =
        caller.call { api.createCuriosity(id, NewCuriosityRequest(text)) }.map { it.toDomain() }

    suspend fun suggestLandmark(req: NewLandmarkRequest): ApiResult<Landmark> =
        caller.call { api.createLandmark(req) }.map { it.toDomain() }
}
