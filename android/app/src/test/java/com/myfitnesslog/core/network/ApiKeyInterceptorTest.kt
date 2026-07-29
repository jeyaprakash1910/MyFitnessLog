package com.myfitnesslog.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies [ApiKeyInterceptor] attaches (or omits) the X-API-Key header on the
 * actual outbound request.
 */
class ApiKeyInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithKey(key: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(key))
            .build()

    private fun fire(client: OkHttpClient) {
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/api/v1/exercises")).build())
            .execute().close()
    }

    @Test
    fun `attaches X-API-Key when a key is configured`() {
        fire(clientWithKey("secret-123"))
        assertEquals("secret-123", server.takeRequest().getHeader("X-API-Key"))
    }

    @Test
    fun `omits X-API-Key when the key is blank`() {
        fire(clientWithKey(""))
        assertNull(server.takeRequest().getHeader("X-API-Key"))
    }

    @Test
    fun `omits X-API-Key when the key is whitespace only`() {
        fire(clientWithKey("   "))
        assertNull(server.takeRequest().getHeader("X-API-Key"))
    }
}
