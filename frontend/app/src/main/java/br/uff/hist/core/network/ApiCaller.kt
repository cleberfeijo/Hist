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
            ApiResult.Failure(toError(value.errorBody()?.string()))
        } else {
            ApiResult.Success(value)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(toError(runCatching { e.response()?.errorBody()?.string() }.getOrNull()))
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network)
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Unknown)
    }

    private fun toError(body: String?): AppError {
        val envelope = body?.let {
            runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), it) }.getOrNull()
        } ?: return AppError.Unknown
        val error = envelope.error
        if (error.code in SESSION_CODES) {
            sessionEvents.notifyExpired()
            return AppError.SessionExpired
        }
        val fields = error.details
            .mapNotNull { d -> d.field?.let { f -> f to (d.message ?: "") } }
            .toMap()
        return AppError.Api(error.code, error.message, fields)
    }

    private companion object {
        val SESSION_CODES = setOf("unauthorized", "session_revoked")
    }
}
