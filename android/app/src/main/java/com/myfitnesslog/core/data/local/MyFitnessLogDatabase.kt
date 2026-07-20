package com.myfitnesslog.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myfitnesslog.core.data.local.converters.InstantConverter
import com.myfitnesslog.core.data.local.converters.SyncStatusConverter
import com.myfitnesslog.core.data.local.converters.UuidConverter
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.local.RoutineDao
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDao
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity

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
 * Pre-release the app uses destructive migration (see DatabaseModule), so no
 * hand-written Migration is required yet; real migrations begin once shipped.
 */
@Database(
    entities = [
        ExerciseCategoryEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, InstantConverter::class, SyncStatusConverter::class)
abstract class MyFitnessLogDatabase : RoomDatabase() {

    abstract fun exerciseCategoryDao(): ExerciseCategoryDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun routineExerciseDao(): RoutineExerciseDao

    companion object {
        const val DATABASE_NAME: String = "myfitnesslog.db"
    }
}
