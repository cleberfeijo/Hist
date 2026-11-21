package br.uff.hist.core.network

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

fun interface TokenProvider {
    fun currentToken(): String?
}

class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Quem já mandou o próprio Authorization (ex.: logout com o token antigo) não é reescrito.
        if (original.header("Authorization") != null) return chain.proceed(original)
        val token = tokenProvider.currentToken()
        val request = if (token.isNullOrEmpty()) {
            original
        } else {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}
