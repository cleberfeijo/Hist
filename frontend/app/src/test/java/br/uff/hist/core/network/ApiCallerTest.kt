package br.uff.hist.core.network

import app.cash.turbine.test
import br.uff.hist.core.session.SessionEvents
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiCallerTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HistApi
    private val events = SessionEvents()
    private val caller = ApiCaller(HistJson, events)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(HistJson.asConverterFactory("application/json".toMediaType()))
            .build().create(HistApi::class.java)
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun enqueueError(code: Int, errCode: String, message: String, details: String = "[]") {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setBody("""{"error":{"code":"$errCode","message":"$message","details":$details}}"""),
        )
    }

    @Test fun `404 vira erro de API sem evento de sessao`() = runTest {
        enqueueError(404, "not_found", "Ponto histórico não encontrado.")
        events.expired.test {
            val result = caller.call { api.landmark("x") }
            assertEquals(
                ApiResult.Failure(AppError.Api("not_found", "Ponto histórico não encontrado.", emptyMap())),
                result,
            )
            expectNoEvents()
        }
    }

    @Test fun `422 preenche fieldErrors`() = runTest {
        enqueueError(422, "validation_error", "Dados inválidos.", """[{"field":"name","message":"curto"}]""")
        val result = caller.call { api.landmark("x") } as ApiResult.Failure
        assertEquals(mapOf("name" to "curto"), (result.error as AppError.Api).fieldErrors)
    }

    @Test fun `401 session_revoked expira a sessao e emite evento`() = runTest {
        enqueueError(401, "session_revoked", "Sessão encerrada.")
        events.expired.test {
            val result = caller.call { api.me() }
            assertEquals(ApiResult.Failure(AppError.SessionExpired), result)
            awaitItem()
        }
    }

    @Test fun `401 unauthorized tambem expira a sessao`() = runTest {
        enqueueError(401, "unauthorized", "Token inválido.")
        events.expired.test {
            assertEquals(ApiResult.Failure(AppError.SessionExpired), caller.call { api.me() })
            awaitItem()
        }
    }

    @Test fun `401 invalid_credentials e erro de formulario sem evento`() = runTest {
        enqueueError(401, "invalid_credentials", "Email ou senha incorretos.")
        events.expired.test {
            val result = caller.call { api.login("a@b.c", "x") }
            assertEquals(
                ApiResult.Failure(AppError.Api("invalid_credentials", "Email ou senha incorretos.", emptyMap())),
                result,
            )
            expectNoEvents()
        }
    }

    @Test fun `rota Response Unit com erro tambem vira falha`() = runTest {
        enqueueError(404, "not_found", "Ponto histórico não encontrado.")
        val result = caller.call { api.deleteLandmark("x") }
        assertEquals(
            ApiResult.Failure(AppError.Api("not_found", "Ponto histórico não encontrado.", emptyMap())),
            result,
        )
    }

    @Test fun `204 em Response Unit e sucesso`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(true, caller.call { api.deleteLandmark("x") } is ApiResult.Success)
    }

    @Test fun `corpo ilegivel vira Unknown`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>oops</html>"))
        assertEquals(ApiResult.Failure(AppError.Unknown), caller.call { api.me() })
    }

    @Test fun `servidor desligado vira Network`() = runTest {
        server.shutdown()
        assertEquals(ApiResult.Failure(AppError.Network), caller.call { api.me() })
    }

    @Test fun `401 sem corpo expira a sessao e emite evento`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        events.expired.test {
            assertEquals(ApiResult.Failure(AppError.SessionExpired), caller.call { api.me() })
            awaitItem()
        }
    }

    @Test fun `401 com corpo ilegivel expira a sessao`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("<html>no</html>"))
        events.expired.test {
            assertEquals(ApiResult.Failure(AppError.SessionExpired), caller.call { api.me() })
            awaitItem()
        }
    }

    @Test fun `401 invalid_password e erro de formulario sem evento`() = runTest {
        enqueueError(401, "invalid_password", "Senha atual incorreta.")
        events.expired.test {
            val result = caller.call { api.me() }
            assertEquals(
                ApiResult.Failure(AppError.Api("invalid_password", "Senha atual incorreta.", emptyMap())),
                result,
            )
            expectNoEvents()
        }
    }

    @Test fun `codigo de sessao e comparado sem diferenciar maiusculas`() = runTest {
        enqueueError(401, "SESSION_REVOKED", "Sessão encerrada.")
        events.expired.test {
            assertEquals(ApiResult.Failure(AppError.SessionExpired), caller.call { api.me() })
            awaitItem()
        }
    }

    @Test fun `500 sem corpo continua Unknown sem evento`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        events.expired.test {
            assertEquals(ApiResult.Failure(AppError.Unknown), caller.call { api.me() })
            expectNoEvents()
        }
    }

    @Test fun `cancelamento propaga`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { caller.call<Unit> { throw CancellationException("x") } }
        }
    }
}
