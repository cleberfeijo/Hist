package br.uff.hist.core.network

import br.uff.hist.core.model.Category
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class HistApiTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() = server.shutdown()

    private fun api(token: String? = null): HistApi = Retrofit.Builder()
        .baseUrl(server.url("/v1/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor { token }).build())
        .addConverterFactory(HistJson.asConverterFactory("application/json".toMediaType()))
        .build().create(HistApi::class.java)

    private val tokenJson = """{"access_token":"t","token_type":"bearer","expires_in":604800,
        "user":{"id":"1","name":"Ana","last_name":"Souza","email":"ana@ex.com","roles":["user"],
        "plan":"free","is_active":true,"created_at":"2026-01-01T00:00:00Z"}}"""

    @Test fun `login envia form urlencoded`() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson))
        api().login("ana@ex.com", "segredo123")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/auth/login", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals("username=ana%40ex.com&password=segredo123", req.body.readUtf8())
    }

    @Test fun `deleteMe envia DELETE com corpo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api().deleteMe(PasswordBody("x"))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/v1/users/me", req.path)
        assertEquals("""{"password":"x"}""", req.body.readUtf8())
    }

    @Test fun `token vira header Bearer`() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson.substringAfter("\"user\":").removeSuffix("}")))
        api("abc").me()
        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `logout envia o token explicito mesmo sem token no interceptor`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api(null).logout("Bearer velho")
        assertEquals("Bearer velho", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `Authorization explicito nao e reescrito pelo token atual`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api("novo").logout("Bearer velho")
        assertEquals("Bearer velho", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `nearby desserializa e mapeia categoria`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"1","name":"Igreja Matriz","description":"d","latitude":-22.52,"longitude":-41.94,
                "category":"igreja","city":"Rio das Ostras","state":"RJ","country":"BR",
                "image_url":"https://x/y.jpg","source":{"name":"wikidata","url":"u","license":"CC0","external_id":"Q123"},
                "status":"approved","curiosity_count":3,"distance_m":87.4,"chat_available":true,
                "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}]""",
            ),
        )
        val items = api().nearby(-22.5, -41.9, 1000, 200).map { it.toDomain() }
        val req = server.takeRequest()
        assertEquals("/v1/landmarks/nearby?lat=-22.5&lon=-41.9&radius=1000&limit=200", req.path)
        assertEquals(Category.IGREJA, items.single().category)
        assertEquals(3, items.single().curiosityCount)
        assertEquals("wikidata", items.single().source?.name)
    }

    @Test fun `curiosidade mapeia autor e data`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"c","landmark_id":"1","landmark_name":"Igreja","text":"texto longo aqui",
                "author":{"id":"u","name":"Ana Souza"},"source":null,"status":"pending",
                "rejection_reason":null,"created_at":"2026-01-01T00:00:00+00:00"}],"total":1,"page":1,"page_size":20}""",
            ),
        )
        val page = api().myCuriosities("pending", 1, 20).toDomain { it.toDomain() }
        assertEquals("Ana Souza", page.items.single().authorName)
        assertEquals(1, page.total)
        assertEquals("/v1/users/me/curiosities?status=pending&page=1&page_size=20", server.takeRequest().path)
    }
}
