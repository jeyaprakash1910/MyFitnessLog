package com.myfitnesslog.feature.workout.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import java.time.Instant
import java.util.UUID

/**
 * One workout — a completed (or in-progress / discarded) gym visit.
 *
 * Part of workout history: history rows are never soft-deleted (no isDeleted).
 * A null [routineId] denotes a manual workout (DATABASE.md). The optional FK to
 * [RoutineEntity] uses SET_NULL to mirror the backend rule "deleting a routine
 * must not delete workout sessions"; since routines are only ever soft-deleted
 * locally, this never actually fires but keeps the model faithful.
 *
 * `userId` is not stored locally (single-user V1; the sync layer attaches it).
 * `syncStatus` is present from creation for the future upload (Milestone 9).
 */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["status"]),
        Index(value = ["startedAt"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "routineId")
    val routineId: UUID?,

    /**
     * The routine's name when this workout started; null for a manual workout, and
     * for sessions recorded before Room v7.
     *
     * Snapshotted like `WorkoutExercise.exerciseName`, so renaming a routine does
     * not relabel the workouts already performed from it (ADR-0004).
     */
    @ColumnInfo(name = "routineName")
    val routineName: String? = null,

    @ColumnInfo(name = "status")
    val status: WorkoutStatus,

    @ColumnInfo(name = "startedAt")
    val startedAt: Instant,

    @ColumnInfo(name = "endedAt")
    val endedAt: Instant? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Instant,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Instant,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
