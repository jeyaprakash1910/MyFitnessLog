package com.myfitnesslog.feature.routine.data.remote

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Entity → DTO mapping tests for the routine upload contract. */
class RoutineSyncMappersTest {

    private val routineId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val routineExerciseId = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val exerciseId = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val now: Instant = Instant.parse("2026-07-21T10:00:00Z")

    private fun routine() = RoutineEntity(
        id = routineId,
        name = "Push Day",
        createdAt = now,
        updatedAt = now,
        isDeleted = false,
        syncStatus = SyncStatus.PENDING,
    )

    private fun routineExercise() = RoutineExerciseEntity(
        id = routineExerciseId,
        routineId = routineId,
        exerciseId = exerciseId,
        displayOrder = 2,
        targetSets = 4,
        minTargetReps = 6,
        maxTargetReps = 10,
        targetRestSeconds = 120,
        notes = "Pause at chest",
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `routine maps to a create request with its client-generated id`() {
        val dto = routine().toCreateRequest()
        assertEquals(routineId.toString(), dto.id)
        assertEquals("Push Day", dto.name)
    }

    @Test
    fun `routine create request omits fields the local schema does not hold`() {
        val dto = routine().toCreateRequest()
        assertNull(dto.description)
        assertNull(dto.displayOrder)
    }

    @Test
    fun `routine maps to an update request without an id in the body`() {
        assertEquals("Push Day", routine().toUpdateRequest().name)
    }

    @Test
    fun `routine exercise maps displayOrder onto the API's exerciseOrder`() {
        val dto = routineExercise().toAddRequest()
        assertEquals(2, dto.exerciseOrder)
        assertEquals(routineExerciseId.toString(), dto.id)
        assertEquals(exerciseId.toString(), dto.exerciseId)
    }

    @Test
    fun `routine exercise maps every target field`() {
        val dto = routineExercise().toAddRequest()
        assertEquals(4, dto.targetSets)
        assertEquals(6, dto.minTargetReps)
        assertEquals(10, dto.maxTargetReps)
        assertEquals(120, dto.targetRestSeconds)
        assertEquals("Pause at chest", dto.notes)
    }

    @Test
    fun `routine exercise preserves null optional fields`() {
        val dto = routineExercise().copy(targetRestSeconds = null, notes = null).toAddRequest()
        assertNull(dto.targetRestSeconds)
        assertNull(dto.notes)
    }

    @Test
    fun `routine exercise update request carries the same mutable fields`() {
        val dto = routineExercise().toUpdateRequest()
        assertEquals(2, dto.exerciseOrder)
        assertEquals(4, dto.targetSets)
        assertEquals(120, dto.targetRestSeconds)
    }
}
