package br.uff.hist.core.network

sealed interface AppError {
    data class Api(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, String>,
    ) : AppError

    data object Network : AppError
    data object SessionExpired : AppError
    data object Unknown : AppError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(f(value))
    is ApiResult.Failure -> this
}
