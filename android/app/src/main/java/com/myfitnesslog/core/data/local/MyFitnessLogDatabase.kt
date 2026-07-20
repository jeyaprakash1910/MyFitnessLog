package com.myfitnesslog.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myfitnesslog.core.data.local.converters.BigDecimalConverter
import com.myfitnesslog.core.data.local.converters.InstantConverter
import com.myfitnesslog.core.data.local.converters.SyncStatusConverter
import com.myfitnesslog.core.data.local.converters.UuidConverter
import com.myfitnesslog.core.data.local.converters.WorkoutEnumConverters
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.history.data.local.WorkoutHistoryDao
import com.myfitnesslog.feature.routine.data.local.RoutineDao
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDao
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseDao
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity

/**
 * The application's Room database — the single source of truth on Android
 * (ADR-0002).
 *
 * Phase 2 introduces the reference-data entities ([ExerciseCategoryEntity],
 * [ExerciseEntity]) and their DAOs. `exportSchema = true` writes the schema to
 * `app/schemas`, which is committed to Git so the local database gains the same
 * migration discipline Flyway provides on the backend.
 *
 * [UuidConverter] and [InstantConverter] are used by every entity's identifiers
 * and timestamps; [SyncStatusConverter] is used by the mutable routine entities.
 *
 * Version history:
 *  - v1: reference data (ExerciseCategory, Exercise).
 *  - v2: routine templates (Routine, RoutineExercise) added in Milestone 5.
 *  - v3: workout history (WorkoutSession, WorkoutExercise, WorkoutSet) added in
 *        Milestone 6.
 * Pre-release the app uses destructive migration (see DatabaseModule), so no
 * hand-written Migration is required yet; real migrations begin once shipped.
 */
@Database(
    entities = [
        ExerciseCategoryEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(
    UuidConverter::class,
    InstantConverter::class,
    SyncStatusConverter::class,
    WorkoutEnumConverters::class,
    BigDecimalConverter::class,
)
abstract class MyFitnessLogDatabase : RoomDatabase() {

    abstract fun exerciseCategoryDao(): ExerciseCategoryDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun routineExerciseDao(): RoutineExerciseDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    abstract fun workoutExerciseDao(): WorkoutExerciseDao

    abstract fun workoutSetDao(): WorkoutSetDao

    abstract fun workoutHistoryDao(): WorkoutHistoryDao

    companion object {
        const val DATABASE_NAME: String = "myfitnesslog.db"
    }
}
