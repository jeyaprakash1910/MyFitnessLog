package com.myfitnesslog.feature.workout.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * One performed set — the smallest unit of workout history.
 *
 * Stores only measured facts (weight, reps, RPE, RIR, timing, category,
 * completion). Derived metrics such as rest duration are computed from
 * startedAt/finishedAt, never stored (DATABASE.md). Weight/RPE/RIR are
 * [BigDecimal] to match the backend DECIMAL columns exactly.
 *
 * Value constraints (weight >= 0, rpe 1..10, etc.) are enforced in the
 * repository/UI layer; Room does not model CHECK constraints, and the backend
 * enforces them authoritatively.
 */
@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutExerciseId", "setNumber"]),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "workoutExerciseId")
    val workoutExerciseId: UUID,

    @ColumnInfo(name = "setNumber")
    val setNumber: Int,

    @ColumnInfo(name = "weight")
    val weight: BigDecimal,

    @ColumnInfo(name = "repetitions")
    val repetitions: Int,

    @ColumnInfo(name = "setCategory")
    val setCategory: SetCategory = SetCategory.WORKING,

    @ColumnInfo(name = "startedAt")
    val startedAt: Instant? = null,

    @ColumnInfo(name = "finishedAt")
    val finishedAt: Instant? = null,

    @ColumnInfo(name = "rpe")
    val rpe: BigDecimal? = null,

    @ColumnInfo(name = "rir")
    val rir: BigDecimal? = null,

    @ColumnInfo(name = "isCompleted")
    val isCompleted: Boolean = true,

    @ColumnInfo(name = "createdAt")
    val createdAt: Instant,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Instant,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
