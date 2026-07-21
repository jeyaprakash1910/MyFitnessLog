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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** Transport tests for [WorkoutLogApi] — the exercises and sets inside a session. */
class WorkoutLogApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: WorkoutLogApi

    private val sessionId = "44444444-4444-4444-8444-444444444444"
    private val workoutExerciseId = "55555555-5555-4555-8555-555555555555"
    private val exerciseId = "33333333-3333-4333-8333-333333333333"
    private val setId = "66666666-6666-4666-8666-666666666666"

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

    private val exerciseJson = """
        {"id":"$workoutExerciseId","workoutSessionId":"$sessionId","exerciseId":"$exerciseId",
         "exerciseName":"Bench Press","exerciseOrder":0,"targetSets":3,
         "minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90}
    """.trimIndent()

    private val setJson = """
        {"id":"$setId","workoutExerciseId":"$workoutExerciseId","setNumber":1,
         "weight":60.50,"repetitions":10,"setCategory":"WORKING",
         "startedAt":"2026-07-21T09:05:00Z","rpe":8.5,"rir":1.0,"isCompleted":true}
    """.trimIndent()

    private fun addExerciseRequest() = AddWorkoutExerciseRequestDto(
        id = workoutExerciseId,
        exerciseId = exerciseId,
        exerciseName = "Bench Press",
        exerciseOrder = 0,
        targetSets = 3,
        minTargetReps = 8,
        maxTargetReps = 12,
        targetRestSeconds = 90,
    )

    private fun addSetRequest() = AddWorkoutSetRequestDto(
        id = setId,
        setNumber = 1,
        weight = BigDecimal("60.50"),
        repetitions = 10,
        setCategory = "WORKING",
        startedAt = Instant.parse("2026-07-21T09:05:00Z"),
        rpe = BigDecimal("8.5"),
        rir = BigDecimal("1.0"),
        isCompleted = true,
    )

    @Test
    fun `addWorkoutExercise POSTs to the session's exercises collection`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(exerciseJson))

        val response = api.addWorkoutExercise(sessionId, addExerciseRequest())

        val request = server.takeRequest()
        request.assertMethodAndPath("POST", "/api/v1/workout-sessions/$sessionId/exercises")
        assertEquals("Bench Press", request.jsonBody()["exerciseName"]!!.jsonPrimitive.content)
        assertEquals("Bench Press", response.body()!!.exerciseName)
        assertEquals(sessionId, response.body()!!.workoutSessionId)
    }

    @Test
    fun `workout-exercise update and delete use the flat top-level path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(exerciseJson))
        api.updateWorkoutExercise(
            workoutExerciseId,
            UpdateWorkoutExerciseRequestDto(
                exerciseName = "Bench Press",
                exerciseOrder = 0,
                targetSets = 3,
                minTargetReps = 8,
                maxTargetReps = 12,
            ),
        )
        server.takeRequest()
            .assertMethodAndPath("PUT", "/api/v1/workout-exercises/$workoutExerciseId")

        server.enqueue(MockResponse().setResponseCode(204))
        val deleted = api.deleteWorkoutExercise(workoutExerciseId)
        server.takeRequest()
            .assertMethodAndPath("DELETE", "/api/v1/workout-exercises/$workoutExerciseId")
        assertEquals(204, deleted.code())
    }

    @Test
    fun `addWorkoutSet POSTs to the workout exercise's sets collection`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(setJson))

        val response = api.addWorkoutSet(workoutExerciseId, addSetRequest())

        server.takeRequest()
            .assertMethodAndPath("POST", "/api/v1/workout-exercises/$workoutExerciseId/sets")
        assertEquals(1, response.body()!!.setNumber)
        assertTrue(response.body()!!.isCompleted)
    }

    @Test
    fun `set weight is sent as an unquoted number preserving scale`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(setJson))

        api.addWorkoutSet(workoutExerciseId, addSetRequest())

        val raw = server.takeRequest().body.readUtf8()
        // The literal text matters: a quoted "60.50" or a collapsed 60.5 would
        // both diverge from the backend's NUMERIC column.
        assertTrue(raw, raw.contains(""""weight":60.50"""))
        assertTrue(raw, raw.contains(""""rpe":8.5"""))
        assertFalse(raw, raw.contains(""""weight":"60.50""""))
    }

    @Test
    fun `set decimals survive the response round trip at full precision`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(setJson))

        val body = api.addWorkoutSet(workoutExerciseId, addSetRequest()).body()!!

        server.takeRequest()
        assertEquals(BigDecimal("60.50"), body.weight)
        assertEquals(2, body.weight.scale())
        assertEquals(BigDecimal("8.5"), body.rpe)
        assertEquals(BigDecimal("1.0"), body.rir)
    }

    @Test
    fun `an omitted optional field deserializes as null`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"$setId","workoutExerciseId":"$workoutExerciseId","setNumber":1,
                    "weight":40,"repetitions":12,"setCategory":"WARMUP","isCompleted":false}""",
            ),
        )

        val body = api.addWorkoutSet(workoutExerciseId, addSetRequest()).body()!!

        server.takeRequest()
        assertEquals(null, body.rpe)
        assertEquals(null, body.finishedAt)
        assertEquals(BigDecimal("40"), body.weight)
        assertFalse(body.isCompleted)
    }

    @Test
    fun `workout-set update and delete use the flat top-level path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(setJson))
        api.updateWorkoutSet(
            setId,
            UpdateWorkoutSetRequestDto(
                setNumber = 1,
                weight = BigDecimal("62.50"),
                repetitions = 8,
                setCategory = "WORKING",
                isCompleted = true,
            ),
        )
        server.takeRequest().assertMethodAndPath("PUT", "/api/v1/workout-sets/$setId")

        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteWorkoutSet(setId)
        server.takeRequest().assertMethodAndPath("DELETE", "/api/v1/workout-sets/$setId")
    }
}
