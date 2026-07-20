package com.myfitnesslog.feature.workout.data.local

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
 * A historical snapshot of one exercise performed during a workout.
 *
 * `exerciseName` and the target fields are copied from the routine at workout
 * start so completed workouts stay accurate even if the routine/exercise is
 * later edited (DATABASE.md snapshot architecture). The FK to the parent session
 * cascades; the FK to the master [ExerciseEntity] is RESTRICT (master data must
 * not disappear from under history).
 */
@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
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
        Index(value = ["workoutSessionId", "exerciseOrder"]),
        Index(value = ["exerciseId"]),
    ],
)
data class WorkoutExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "workoutSessionId")
    val workoutSessionId: UUID,

    @ColumnInfo(name = "exerciseId")
    val exerciseId: UUID,

    @ColumnInfo(name = "exerciseName")
    val exerciseName: String,

    @ColumnInfo(name = "exerciseOrder")
    val exerciseOrder: Int,

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

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
