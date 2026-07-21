package com.myfitnesslog.core.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Shared MockWebServer plumbing for the sync transport tests.
 *
 * The [Json] instance is configured identically to NetworkModule's, so these
 * tests exercise the same serialization behaviour the app will use — in
 * particular `explicitNulls = false`, which omits null fields from request
 * bodies rather than sending explicit nulls.
 */
val syncTestJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Builds a Retrofit API bound to [server], mirroring NetworkModule's converter
 * setup. The base URL ends in `/api/v1/` exactly as the real one does, so the
 * relative paths declared on the interfaces are resolved the same way here.
 */
inline fun <reified T : Any> MockWebServer.createApi(): T =
    Retrofit.Builder()
        .baseUrl(url("/api/v1/"))
        .addConverterFactory(syncTestJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(T::class.java)

/** Asserts the request's HTTP method and path, the two halves of the contract. */
fun RecordedRequest.assertMethodAndPath(method: String, path: String) {
    assertEquals(method, this.method)
    assertEquals(path, this.path)
}

/** Parses the recorded request body as a JSON object for field-level assertions. */
fun RecordedRequest.jsonBody(): JsonObject =
    syncTestJson.parseToJsonElement(body.readUtf8()) as JsonObject
