package com.myfitnesslog.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the `X-API-Key` header to every backend request (ADR-0013).
 *
 * The key comes from build configuration ([com.myfitnesslog.BuildConfig.API_KEY],
 * populated from `apiKey`/`MFL_API_KEY` at build time). When it is blank — local
 * development against a backend with authentication disabled — the header is
 * omitted, so no code path changes between an authenticated and an unauthenticated
 * backend.
 *
 * This authenticates the application, not a user. Replacing it (or adding a second
 * interceptor) is the seam for real per-user auth later; the rest of the networking
 * stack is unaffected.
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (apiKey.isBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header(HEADER, apiKey)
                .build()
        )
    }

    companion object {
        const val HEADER = "X-API-Key"
    }
}
