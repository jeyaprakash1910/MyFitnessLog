package com.myfitnesslog.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myfitnesslog.core.data.local.converters.InstantConverter
import com.myfitnesslog.core.data.local.converters.UuidConverter
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity

/**
 * The application's Room database — the single source of truth on Android
 * (ADR-0002).
 *
 * Phase 2 introduces the reference-data entities ([ExerciseCategoryEntity],
 * [ExerciseEntity]) and their DAOs. `exportSchema = true` writes the schema to
 * `app/schemas`, which is committed to Git so the local database gains the same
 * migration discipline Flyway provides on the backend.
 *
 * [InstantConverter] is registered ahead of its first use: the workout-history
 * entities added in later milestones store timestamps. [UuidConverter] is used
 * by every entity's identifiers.
 */
@Database(
    entities = [
        ExerciseCategoryEntity::class,
        ExerciseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, InstantConverter::class)
abstract class MyFitnessLogDatabase : RoomDatabase() {

    abstract fun exerciseCategoryDao(): ExerciseCategoryDao

    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val DATABASE_NAME: String = "myfitnesslog.db"
    }
}
