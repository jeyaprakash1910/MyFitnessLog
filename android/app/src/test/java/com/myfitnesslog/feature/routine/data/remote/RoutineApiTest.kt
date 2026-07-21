package com.myfitnesslog.feature.routine.data.remote

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Transport tests for [RoutineApi]: they assert the HTTP method, path, and
 * serialized body of each call against the backend contract, and that responses
 * deserialize. No synchronization logic is involved.
 */
class RoutineApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RoutineApi

    private val routineId = "11111111-1111-4111-8111-111111111111"
    private val routineExerciseId = "22222222-2222-4222-8222-222222222222"
    private val exerciseId = "33333333-3333-4333-8333-333333333333"

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

    @Test
    fun `createRoutine POSTs to routines and reports 201 Created`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"id":"$routineId","name":"Push Day","displayOrder":0}"""),
        )

        val response = api.createRoutine(
            CreateRoutineRequestDto(id = routineId, name = "Push Day"),
        )

        server.takeRequest().assertMethodAndPath("POST", "/api/v1/routines")
        assertEquals(201, response.code())
        assertEquals("Push Day", response.body()!!.name)
    }

    @Test
    fun `createRoutine reports 200 OK when the backend updated an existing routine`() = runTest {
        // The idempotent-save signal the sync engine will rely on in Phase 2.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":"$routineId","name":"Push Day","displayOrder":0}"""),
        )

        val response = api.createRoutine(CreateRoutineRequestDto(id = routineId, name = "Push Day"))

        server.takeRequest()
        assertEquals(200, response.code())
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `createRoutine sends the client id and omits absent optional fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"$routineId","name":"Push Day","displayOrder":0}"""))

        api.createRoutine(CreateRoutineRequestDto(id = routineId, name = "Push Day"))

        val body = server.takeRequest().jsonBody()
        assertEquals(routineId, body["id"]!!.jsonPrimitive.content)
        assertEquals("Push Day", body["name"]!!.jsonPrimitive.content)
        // explicitNulls = false: unset optionals are omitted, not sent as null.
        assertFalse(body.containsKey("description"))
        assertFalse(body.containsKey("displayOrder"))
    }

    @Test
    fun `updateRoutine PUTs to the routine's path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"$routineId","name":"Pull Day","displayOrder":0}"""))

        val response = api.updateRoutine(routineId, UpdateRoutineRequestDto(name = "Pull Day"))

        server.takeRequest().assertMethodAndPath("PUT", "/api/v1/routines/$routineId")
        assertEquals("Pull Day", response.body()!!.name)
    }

    @Test
    fun `deleteRoutine DELETEs the routine and tolerates an empty 204 body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = api.deleteRoutine(routineId)

        server.takeRequest().assertMethodAndPath("DELETE", "/api/v1/routines/$routineId")
        assertEquals(204, response.code())
    }

    @Test
    fun `addRoutineExercise POSTs to the routine's exercises collection`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"$routineExerciseId","exerciseId":"$exerciseId","exerciseOrder":2,
                   "targetSets":4,"minTargetReps":6,"maxTargetReps":10,"targetRestSeconds":120}""",
            ),
        )

        val response = api.addRoutineExercise(
            routineId,
            AddRoutineExerciseRequestDto(
                id = routineExerciseId,
                exerciseId = exerciseId,
                exerciseOrder = 2,
                targetSets = 4,
                minTargetReps = 6,
                maxTargetReps = 10,
                targetRestSeconds = 120,
            ),
        )

        val request = server.takeRequest()
        request.assertMethodAndPath("POST", "/api/v1/routines/$routineId/exercises")
        assertEquals(2, request.jsonBody()["exerciseOrder"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, response.body()!!.exerciseOrder)
        assertNull(response.body()!!.notes)
    }

    @Test
    fun `routine-exercise update and delete use the flat top-level path`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"$routineExerciseId","exerciseId":"$exerciseId","exerciseOrder":1,
                   "targetSets":3,"minTargetReps":8,"maxTargetReps":12}""",
            ),
        )
        api.updateRoutineExercise(
            routineExerciseId,
            UpdateRoutineExerciseRequestDto(
                exerciseOrder = 1, targetSets = 3, minTargetReps = 8, maxTargetReps = 12,
            ),
        )
        server.takeRequest()
            .assertMethodAndPath("PUT", "/api/v1/routine-exercises/$routineExerciseId")

        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteRoutineExercise(routineExerciseId)
        server.takeRequest()
            .assertMethodAndPath("DELETE", "/api/v1/routine-exercises/$routineExerciseId")
    }

    @Test
    fun `a server error is returned as an unsuccessful response, not thrown`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

        val response = api.createRoutine(CreateRoutineRequestDto(id = routineId, name = "Push Day"))

        server.takeRequest()
        assertFalse(response.isSuccessful)
        assertEquals(500, response.code())
    }
}
