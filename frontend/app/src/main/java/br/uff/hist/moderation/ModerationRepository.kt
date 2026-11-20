package br.uff.hist.moderation

import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.Page
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.RejectRequest
import br.uff.hist.core.network.map
import br.uff.hist.core.network.toDomain
import javax.inject.Inject

internal const val MODERATION_PAGE_SIZE = 20
private const val STATUS_PENDING = "pending"

class ModerationRepository @Inject constructor(
    private val api: HistApi,
    private val caller: ApiCaller,
) {
    suspend fun landmarkQueue(page: Int): ApiResult<Page<Landmark>> =
        caller.call { api.moderationLandmarks(STATUS_PENDING, page, MODERATION_PAGE_SIZE) }
            .map { dto -> dto.toDomain { it.toDomain() } }

    suspend fun curiosityQueue(page: Int): ApiResult<Page<Curiosity>> =
        caller.call { api.moderationCuriosities(STATUS_PENDING, page, MODERATION_PAGE_SIZE) }
            .map { dto -> dto.toDomain { it.toDomain() } }

    suspend fun landmark(id: String): ApiResult<Landmark> = caller.call { api.landmark(id) }.map { it.toDomain() }

    suspend fun curiosity(id: String): ApiResult<Curiosity> = caller.call { api.curiosity(id) }.map { it.toDomain() }

    suspend fun approveLandmark(id: String): ApiResult<Unit> = caller.call { api.approveLandmark(id) }.map { }

    suspend fun rejectLandmark(id: String, reason: String): ApiResult<Unit> =
        caller.call { api.rejectLandmark(id, RejectRequest(reason)) }.map { }

    suspend fun approveCuriosity(id: String): ApiResult<Unit> = caller.call { api.approveCuriosity(id) }.map { }

    suspend fun rejectCuriosity(id: String, reason: String): ApiResult<Unit> =
        caller.call { api.rejectCuriosity(id, RejectRequest(reason)) }.map { }
}
