package com.myfitnesslog.feature.workout.data.remote

import com.myfitnesslog.core.data.remote.assertMethodAndPath
import com.myfitnesslog.core.data.remote.createApi
import com.myfitnesslog.core.data.remote.jsonBody
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Transport tests for [WorkoutSessionApi] — the session lifecycle endpoints. */
class WorkoutSessionApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: WorkoutSessionApi

    private val sessionId = "44444444-4444-4444-8444-444444444444"
    private val routineId = "11111111-1111-4111-8111-111111111111"
    private val startedAt: Instant = Instant.parse("2026-07-21T09:00:00Z")
    private val endedAt: Instant = Instant.parse("2026-07-21T10:30:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = server.createApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sessionJson(status: String, ended: String?) = buildString {
        append("""{"id":"$sessionId","routineId":"$routineId","status":"$status",""")
        append(""""startedAt":"2026-07-21T09:00:00Z"""")
        if (ended != null) append(""","endedAt":"$ended"""")
        append("}")
    }

    @Test
    fun `startWorkoutSession POSTs to workout-sessions with an ISO-8601 timestamp`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(sessionJson("IN_PROGRESS", null)),
        )

        val response = api.startWorkoutSession(
            StartWorkoutSessionRequestDto(
                id = sessionId, routineId = routineId, startedAt = startedAt,
            ),
        )

        val request = server.takeRequest()
        request.assertMethodAndPath("POST", "/api/v1/workout-sessions")
        assertEquals(
            "2026-07-21T09:00:00Z",
            request.jsonBody()["startedAt"]!!.jsonPrimitive.content,
        )
        assertEquals(startedAt, response.body()!!.startedAt)
        assertNull(response.body()!!.endedAt)
    }

    @Test
    fun `a manual workout omits routineId from the request body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(sessionJson("IN_PROGRESS", null)))

        api.startWorkoutSession(
            StartWorkoutSessionRequestDto(id = sessionId, routineId = null, startedAt = startedAt),
        )

        assertFalse(server.takeRequest().jsonBody().containsKey("routineId"))
    }

    @Test
    fun `completeWorkoutSession PUTs to the complete sub-path`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(sessionJson("COMPLETED", "2026-07-21T10:30:00Z")),
        )

        val response = api.completeWorkoutSession(
            sessionId,
            CompleteWorkoutSessionRequestDto(endedAt = endedAt, notes = "Felt strong"),
        )

        val request = server.takeRequest()
        request.assertMethodAndPath("PUT", "/api/v1/workout-sessions/$sessionId/complete")
        val body = request.jsonBody()
        assertEquals("2026-07-21T10:30:00Z", body["endedAt"]!!.jsonPrimitive.content)
        assertEquals("Felt strong", body["notes"]!!.jsonPrimitive.content)
        assertEquals("COMPLETED", response.body()!!.status)
        assertEquals(endedAt, response.body()!!.endedAt)
    }

    @Test
    fun `discardWorkoutSession PUTs to the discard sub-path`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(sessionJson("DISCARDED", "2026-07-21T10:30:00Z")),
        )

        val response = api.discardWorkoutSession(
            sessionId,
            DiscardWorkoutSessionRequestDto(endedAt = endedAt),
        )

        server.takeRequest()
            .assertMethodAndPath("PUT", "/api/v1/workout-sessions/$sessionId/discard")
        assertEquals("DISCARDED", response.body()!!.status)
    }

    @Test
    fun `an unknown status value deserializes rather than throwing`() = runTest {
        // status is carried as String precisely so a newer backend cannot break
        // an older client at the transport layer.
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionJson("ARCHIVED", null)))

        val response = api.startWorkoutSession(
            StartWorkoutSessionRequestDto(id = sessionId, startedAt = startedAt),
        )

        server.takeRequest()
        assertEquals("ARCHIVED", response.body()!!.status)
    }
}
