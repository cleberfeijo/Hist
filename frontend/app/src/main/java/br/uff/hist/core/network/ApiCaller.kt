package br.uff.hist.core.network

import br.uff.hist.core.session.SessionEvents
import java.io.IOException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response

class ApiCaller @Inject constructor(
    private val json: Json,
    private val sessionEvents: SessionEvents,
) {
    suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        val value = block()
        if (value is Response<*> && !value.isSuccessful) {
            ApiResult.Failure(toError(value.code(), runCatching { value.errorBody()?.string() }.getOrNull()))
        } else {
            ApiResult.Success(value)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(
            toError(e.code(), runCatching { e.response()?.errorBody()?.string() }.getOrNull()),
        )
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network)
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Unknown)
    }

    private fun toError(status: Int, body: String?): AppError {
        val envelope = body?.let {
            runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), it) }.getOrNull()
        }
        if (envelope == null) {
            // 401 sem envelope legível (proxy, gateway, corpo vazio): o token não vale mais.
            if (status == HTTP_UNAUTHORIZED) {
                sessionEvents.notifyExpired()
                return AppError.SessionExpired
            }
            return AppError.Unknown
        }
        val error = envelope.error
        if (error.code.lowercase() in SESSION_CODES) {
            sessionEvents.notifyExpired()
            return AppError.SessionExpired
        }
        val fields = error.details
            .mapNotNull { d -> d.field?.let { f -> f to (d.message ?: "") } }
            .toMap()
        return AppError.Api(error.code, error.message, fields)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        val SESSION_CODES = setOf("unauthorized", "session_revoked")
    }
}
