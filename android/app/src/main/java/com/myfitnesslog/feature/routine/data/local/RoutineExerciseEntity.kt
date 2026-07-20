package com.myfitnesslog.feature.routine.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import java.time.Instant
import java.util.UUID

/**
 * One planned exercise within a routine, including its targets.
 *
 * Foreign keys mirror the backend rules (DATABASE.md): the parent [RoutineEntity]
 * cascades deletes, and the referenced [ExerciseEntity] is RESTRICT (master data
 * must not disappear from under a routine). `displayOrder` is the exercise
 * position within the routine (the backend's exerciseOrder).
 *
 * Targets (targetSets, minTargetReps, maxTargetReps, targetRestSeconds, notes)
 * are captured now to match ANDROID_FLOW's Routine Detail/Edit screens and the
 * backend's NOT NULL columns, so Milestone 9 sync can populate them without a
 * Room migration.
 */
@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["exerciseId"]),
    ],
)
data class RoutineExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "routineId")
    val routineId: UUID,

    @ColumnInfo(name = "exerciseId")
    val exerciseId: UUID,

    @ColumnInfo(name = "displayOrder")
    val displayOrder: Int,

    @ColumnInfo(name = "targetSets")
    val targetSets: Int,

    @ColumnInfo(name = "minTargetReps")
    val minTargetReps: Int,

    @ColumnInfo(name = "maxTargetReps")
    val maxTargetReps: Int,

    @ColumnInfo(name = "targetRestSeconds")
    val targetRestSeconds: Int? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Instant,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Instant,

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
