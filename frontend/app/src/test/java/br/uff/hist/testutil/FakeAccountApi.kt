package br.uff.hist.testutil

import br.uff.hist.core.network.ChangePasswordRequest
import br.uff.hist.core.network.CuriosityDto
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.LandmarkDto
import br.uff.hist.core.network.PageDto
import br.uff.hist.core.network.PasswordBody
import br.uff.hist.core.network.TokenResponseDto
import br.uff.hist.core.network.UpdateMeRequest
import br.uff.hist.core.network.UserDto
import retrofit2.Response

fun landmarkDto(id: String, status: String = "approved", reason: String? = null) = LandmarkDto(
    id = id, name = "Ponto $id", latitude = 0.0, longitude = 0.0, status = status, rejectionReason = reason,
)

fun curiosityDto(id: String, status: String = "approved") = CuriosityDto(
    id = id, landmarkId = "p1", landmarkName = "Igreja", text = "Curiosidade $id", status = status,
    createdAt = "2026-01-01T10:00:00Z",
)

/** Só as chamadas de conta e "minhas contribuições"; o resto falha alto. */
class FakeAccountApi : HistApi by unsupportedApi() {
    var landmarkPages: Map<Int, PageDto<LandmarkDto>> = mapOf(1 to PageDto(emptyList(), 0, 1, 20))
    var curiosityPages: Map<Int, PageDto<CuriosityDto>> = mapOf(1 to PageDto(emptyList(), 0, 1, 20))
    val landmarkPageCalls = mutableListOf<Int>()
    val curiosityPageCalls = mutableListOf<Int>()
    val deletedLandmarks = mutableListOf<String>()
    val deletedCuriosities = mutableListOf<String>()
    var deleteFailure: Throwable? = null
    var updateHandler: suspend (UpdateMeRequest) -> UserDto = { UserDto("1", it.name ?: "Ana", it.lastName ?: "Silva", it.email ?: "ana@x.com", listOf("user")) }
    var passwordHandler: suspend (ChangePasswordRequest) -> TokenResponseDto = { tokenResponse("novo") }
    var deleteMeHandler: suspend (PasswordBody) -> Response<Unit> = { Response.success(Unit) }
    var meHandler: suspend () -> UserDto = { UserDto("1", "Ana", "Silva", "ana@x.com", listOf("user")) }
    var changePasswordCalls = 0
    var deleteMeCalls = 0
    var updateCalls = 0
    var lastUpdate: UpdateMeRequest? = null

    override suspend fun me(): UserDto = meHandler()

    override suspend fun myLandmarks(status: String?, page: Int, pageSize: Int): PageDto<LandmarkDto> {
        landmarkPageCalls += page
        return landmarkPages.getValue(page)
    }

    override suspend fun myCuriosities(status: String?, page: Int, pageSize: Int): PageDto<CuriosityDto> {
        curiosityPageCalls += page
        return curiosityPages.getValue(page)
    }

    override suspend fun deleteLandmark(id: String): Response<Unit> {
        deletedLandmarks += id
        deleteFailure?.let { throw it }
        return Response.success(Unit)
    }

    override suspend fun deleteCuriosity(id: String): Response<Unit> {
        deletedCuriosities += id
        deleteFailure?.let { throw it }
        return Response.success(Unit)
    }

    override suspend fun updateMe(body: UpdateMeRequest): UserDto {
        updateCalls++
        lastUpdate = body
        return updateHandler(body)
    }

    override suspend fun changePassword(body: ChangePasswordRequest): TokenResponseDto {
        changePasswordCalls++
        return passwordHandler(body)
    }

    override suspend fun deleteMe(body: PasswordBody): Response<Unit> {
        deleteMeCalls++
        return deleteMeHandler(body)
    }
}
