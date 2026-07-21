package com.myfitnesslog.feature.workout.data.remote

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Entity → DTO mapping tests for the workout upload contract. */
class WorkoutSyncMappersTest {

    private val sessionId = UUID.fromString("44444444-4444-4444-8444-444444444444")
    private val routineId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val workoutExerciseId = UUID.fromString("55555555-5555-4555-8555-555555555555")
    private val exerciseId = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val setId = UUID.fromString("66666666-6666-4666-8666-666666666666")

    private val startedAt: Instant = Instant.parse("2026-07-21T09:00:00Z")
    private val endedAt: Instant = Instant.parse("2026-07-21T10:30:00Z")

    private fun session(
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        endedAt: Instant? = this.endedAt,
    ) = WorkoutSessionEntity(
        id = sessionId,
        routineId = routineId,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        notes = "Felt strong",
        createdAt = startedAt,
        updatedAt = startedAt,
    )

    private fun workoutExercise() = WorkoutExerciseEntity(
        id = workoutExerciseId,
        workoutSessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = "Bench Press",
        exerciseOrder = 0,
        targetSets = 3,
        minTargetReps = 8,
        maxTargetReps = 12,
        targetRestSeconds = 90,
        notes = null,
        createdAt = startedAt,
        updatedAt = startedAt,
    )

    private fun workoutSet() = WorkoutSetEntity(
        id = setId,
        workoutExerciseId = workoutExerciseId,
        setNumber = 1,
        weight = BigDecimal("60.50"),
        repetitions = 10,
        setCategory = SetCategory.WORKING,
        startedAt = startedAt,
        finishedAt = endedAt,
        rpe = BigDecimal("8.5"),
        rir = BigDecimal("1.0"),
        isCompleted = true,
        createdAt = startedAt,
        updatedAt = startedAt,
    )

    @Test
    fun `session maps to a start request preserving the client timestamp`() {
        val dto = session().toStartRequest()
        assertEquals(sessionId.toString(), dto.id)
        assertEquals(routineId.toString(), dto.routineId)
        assertEquals(startedAt, dto.startedAt)
        assertEquals("Felt strong", dto.notes)
    }

    @Test
    fun `a manual workout sends a null routineId`() {
        assertNull(session().copy(routineId = null).toStartRequest().routineId)
    }

    @Test
    fun `session maps to complete and discard requests carrying endedAt`() {
        assertEquals(endedAt, session().toCompleteRequest().endedAt)
        assertEquals(endedAt, session(WorkoutStatus.DISCARDED).toDiscardRequest().endedAt)
    }

    @Test
    fun `mapping a terminal transition for an unfinished session fails loudly`() {
        val inProgress = session(status = WorkoutStatus.IN_PROGRESS, endedAt = null)
        val error = assertThrows(IllegalArgumentException::class.java) {
            inProgress.toCompleteRequest()
        }
        assertTrue(error.message!!.contains("endedAt is null"))
        assertThrows(IllegalArgumentException::class.java) { inProgress.toDiscardRequest() }
    }

    @Test
    fun `workout exercise maps its snapshot fields`() {
        val dto = workoutExercise().toAddRequest()
        assertEquals(workoutExerciseId.toString(), dto.id)
        assertEquals(exerciseId.toString(), dto.exerciseId)
        assertEquals("Bench Press", dto.exerciseName)
        assertEquals(0, dto.exerciseOrder)
        assertEquals(3, dto.targetSets)
        assertNull(dto.notes)
    }

    @Test
    fun `workout exercise update request carries the mutable snapshot fields`() {
        val dto = workoutExercise().toUpdateRequest()
        assertEquals("Bench Press", dto.exerciseName)
        assertEquals(90, dto.targetRestSeconds)
    }

    @Test
    fun `set maps decimals without changing scale`() {
        val dto = workoutSet().toAddRequest()
        assertEquals(BigDecimal("60.50"), dto.weight)
        assertEquals(2, dto.weight.scale())
        assertEquals(BigDecimal("8.5"), dto.rpe)
        assertEquals(BigDecimal("1.0"), dto.rir)
    }

    @Test
    fun `set maps its enum category to the backend's name form`() {
        assertEquals("WORKING", workoutSet().toAddRequest().setCategory)
        assertEquals(
            "WARMUP",
            workoutSet().copy(setCategory = SetCategory.WARMUP).toAddRequest().setCategory,
        )
    }

    @Test
    fun `set preserves optional timing and effort fields when null`() {
        val dto = workoutSet().copy(
            startedAt = null,
            finishedAt = null,
            rpe = null,
            rir = null,
        ).toAddRequest()
        assertNull(dto.startedAt)
        assertNull(dto.finishedAt)
        assertNull(dto.rpe)
        assertNull(dto.rir)
    }

    @Test
    fun `set update request carries the same measured fields`() {
        val dto = workoutSet().toUpdateRequest()
        assertEquals(1, dto.setNumber)
        assertEquals(BigDecimal("60.50"), dto.weight)
        assertEquals(10, dto.repetitions)
        assertTrue(dto.isCompleted)
    }
}
