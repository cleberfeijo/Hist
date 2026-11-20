package br.uff.hist.testutil

import br.uff.hist.core.network.HistApi
import br.uff.hist.core.network.RegisterRequest
import br.uff.hist.core.network.TokenResponseDto
import br.uff.hist.core.network.UserDto
import java.lang.reflect.Proxy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

private fun unsupportedApi(): HistApi = Proxy.newProxyInstance(
    HistApi::class.java.classLoader,
    arrayOf(HistApi::class.java),
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as HistApi

fun httpError(status: Int, code: String, message: String, details: String = "[]"): HttpException =
    HttpException(
        Response.error<Any>(
            status,
            """{"error":{"code":"$code","message":"$message","details":$details}}"""
                .toResponseBody("application/json".toMediaType()),
        ),
    )

fun tokenResponse(token: String = "tok") =
    TokenResponseDto(token, "bearer", 3600, UserDto("1", "Ana", "Silva", "ana@x.com", listOf("user")))

/** Só `login`, `register` e `logout` são implementados; o resto falha alto. */
class FakeAuthApi : HistApi by unsupportedApi() {
    var loginCalls = 0
    var registerCalls = 0
    var logoutCalls = 0
    var lastRegister: RegisterRequest? = null
    var loginHandler: suspend (String, String) -> TokenResponseDto = { _, _ -> tokenResponse() }
    var registerHandler: suspend (RegisterRequest) -> TokenResponseDto = { tokenResponse() }
    var logoutHandler: suspend () -> Response<Unit> = { Response.success(Unit) }

    override suspend fun login(username: String, password: String): TokenResponseDto {
        loginCalls++
        return loginHandler(username, password)
    }

    override suspend fun register(body: RegisterRequest): TokenResponseDto {
        registerCalls++
        lastRegister = body
        return registerHandler(body)
    }

    override suspend fun logout(): Response<Unit> {
        logoutCalls++
        return logoutHandler()
    }
}
