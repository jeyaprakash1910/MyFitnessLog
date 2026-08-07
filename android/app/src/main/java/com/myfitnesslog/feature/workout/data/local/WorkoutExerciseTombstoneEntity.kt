package com.myfitnesslog.feature.workout.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Records that a workout exercise was removed locally, so the deletion can be
 * replayed to the backend (TD-014, mirroring the set tombstones of ADR-0007).
 *
 * Removing an exercise mid-workout already tombstoned its **sets**, and those
 * converged correctly. The exercise row itself did not: it was deleted locally and
 * the backend was never told, so an exercise that had been added, synced and then
 * removed lingered there forever. Harmless while nothing read the backend back.
 * Not harmless once the phone does: a refresh (ADR-0017 Stage 2) would download
 * that stale row and **resurrect on the phone an exercise the user deleted**. A
 * cosmetic residue becomes a correctness bug the moment synchronisation gains its
 * second direction, which is why this is fixed before Stage 2 rather than after.
 *
 * A tombstone rather than a soft-delete flag on the row itself: the row is gone
 * locally, and keeping a deleted exercise around purely to remember to delete it
 * remotely would leak "pending upload" concerns into every query that reads
 * exercises. The set tombstones already established this shape.
 *
 * @param workoutSessionId denormalised so the sync engine can skip deletions
 *                         belonging to a session whose own upload failed, without
 *                         a join to a row that no longer exists
 */
@Entity(
    tableName = "workout_exercise_tombstone",
    indices = [Index(value = ["workoutSessionId"])],
)
data class WorkoutExerciseTombstoneEntity(
    /** Id of the deleted exercise: the path segment the DELETE request uses. */
    @PrimaryKey
    @ColumnInfo(name = "workoutExerciseId")
    val workoutExerciseId: UUID,

    @ColumnInfo(name = "workoutSessionId")
    val workoutSessionId: UUID,

    @ColumnInfo(name = "deletedAt")
    val deletedAt: Instant,
)
