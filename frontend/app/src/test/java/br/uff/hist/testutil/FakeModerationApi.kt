package br.uff.hist.testutil

import br.uff.hist.core.network.CuriosityDto
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.network.PageDto
import br.uff.hist.core.network.RejectRequest

/** Só as chamadas de moderação e os detalhes; o resto falha alto. */
class FakeModerationApi : HistApi by unsupportedApi() {
    var landmarkQueue: List<LandmarkDto> = emptyList()
    var curiosityQueue: List<CuriosityDto> = emptyList()
    val queueStatuses = mutableListOf<String>()
    val approved = mutableListOf<String>()
    val rejected = mutableListOf<Pair<String, String>>()
    var decisionFailure: Throwable? = null
    var detailFailure: Throwable? = null
    /** Se definido, a decisão espera por este bloqueio (para simular toque duplo). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun moderationLandmarks(status: String, page: Int, pageSize: Int): PageDto<LandmarkDto> {
        queueStatuses += status
        return PageDto(landmarkQueue, landmarkQueue.size, 1, pageSize)
    }

    override suspend fun moderationCuriosities(status: String, page: Int, pageSize: Int): PageDto<CuriosityDto> {
        queueStatuses += status
        return PageDto(curiosityQueue, curiosityQueue.size, 1, pageSize)
    }

    override suspend fun landmark(id: String): LandmarkDto {
        detailFailure?.let { throw it }
        return landmarkDto(id, "pending")
    }

    override suspend fun curiosity(id: String): CuriosityDto {
        detailFailure?.let { throw it }
        return curiosityDto(id, "pending")
    }

    override suspend fun approveLandmark(id: String): LandmarkDto {
        approved += id
        gate?.await()
        decisionFailure?.let { throw it }
        return landmarkDto(id, "approved")
    }

    override suspend fun rejectLandmark(id: String, body: RejectRequest): LandmarkDto {
        rejected += id to body.reason
        gate?.await()
        decisionFailure?.let { throw it }
        return landmarkDto(id, "rejected")
    }

    override suspend fun approveCuriosity(id: String): CuriosityDto {
        approved += id
        gate?.await()
        decisionFailure?.let { throw it }
        return curiosityDto(id, "approved")
    }

    override suspend fun rejectCuriosity(id: String, body: RejectRequest): CuriosityDto {
        rejected += id to body.reason
        gate?.await()
        decisionFailure?.let { throw it }
        return curiosityDto(id, "rejected")
    }
}
