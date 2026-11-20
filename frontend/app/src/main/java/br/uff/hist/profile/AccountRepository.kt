package br.uff.hist.profile

import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.Page
import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.ChangePasswordRequest
import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.PasswordBody
import br.uff.hist.core.network.UpdateMeRequest
import br.uff.hist.core.network.map
import br.uff.hist.core.network.toDomain
import br.uff.hist.core.session.SessionStore
import javax.inject.Inject

internal const val CONTRIBUTIONS_PAGE_SIZE = 20

class AccountRepository @Inject constructor(
    private val api: HistApi,
    private val caller: ApiCaller,
    private val store: SessionStore,
) {
    suspend fun refreshMe(): ApiResult<User> = saveUser(caller.call { api.me() }.map { it.toDomain() })

    suspend fun updateName(name: String, lastName: String): ApiResult<User> =
        saveUser(caller.call { api.updateMe(UpdateMeRequest(name = name, lastName = lastName)) }.map { it.toDomain() })

    suspend fun updateEmail(email: String, currentPassword: String): ApiResult<User> =
        saveUser(
            caller.call { api.updateMe(UpdateMeRequest(email = email, currentPassword = currentPassword)) }
                .map { it.toDomain() },
        )

    /** O servidor revoga os tokens antigos e devolve um novo: sem [SessionStore.replaceToken] a próxima chamada cairia no Login. */
    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        when (val result = caller.call { api.changePassword(ChangePasswordRequest(current, new)) }) {
            is ApiResult.Success -> {
                store.replaceToken(result.value.accessToken)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }

    suspend fun deleteAccount(password: String): ApiResult<Unit> {
        val result = caller.call { api.deleteMe(PasswordBody(password)) }.map { }
        if (result is ApiResult.Success) store.clear()
        return result
    }

    suspend fun myLandmarks(page: Int): ApiResult<Page<Landmark>> =
        caller.call { api.myLandmarks(null, page, CONTRIBUTIONS_PAGE_SIZE) }
            .map { dto -> dto.toDomain { it.toDomain() } }

    suspend fun myCuriosities(page: Int): ApiResult<Page<Curiosity>> =
        caller.call { api.myCuriosities(null, page, CONTRIBUTIONS_PAGE_SIZE) }
            .map { dto -> dto.toDomain { it.toDomain() } }

    suspend fun deleteLandmark(id: String): ApiResult<Unit> = caller.call { api.deleteLandmark(id) }.map { }

    suspend fun deleteCuriosity(id: String): ApiResult<Unit> = caller.call { api.deleteCuriosity(id) }.map { }

    private suspend fun saveUser(result: ApiResult<User>): ApiResult<User> {
        if (result is ApiResult.Success) store.updateUser(result.value)
        return result
    }
}
