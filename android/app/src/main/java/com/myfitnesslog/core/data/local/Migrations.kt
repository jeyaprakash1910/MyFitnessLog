package com.myfitnesslog.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Room migrations for MyFitnessLogDatabase.
 *
 * ## The rule
 *
 * **Every schema change ships with a `Migration` here and a case in
 * `MigrationTest`.** No exceptions.
 *
 * The database no longer falls back to a destructive upgrade. That fallback was
 * acceptable only while the app held nothing a user would miss; once real
 * workouts are being logged it would silently delete a training history that may
 * exist nowhere else — anything not yet synchronized is gone permanently.
 *
 * Removing it means a version bump with no matching migration **throws at
 * startup** instead. That is the intended trade: a loud, recoverable failure in
 * development beats a silent, permanent one on someone's phone. `MigrationTest`
 * is what turns that runtime failure into a failing test first — and since this
 * repository has no CI, that test is the only thing enforcing the policy.
 *
 * ## Why the list starts at 3
 *
 * Version 3 is the baseline for real use. Versions 1 and 2 existed only during
 * Milestones 3–6, on developer machines that were being destructively upgraded
 * anyway. Writing 1→2 and 2→3 migrations now would be theatre: no installation
 * that needs them exists, and untested migration code is worse than none.
 *
 * A developer holding a v1 or v2 database from that era will get the loud failure
 * described above and should reinstall the app.
 */
/**
 * v3 → v4: adds `workout_set_tombstone` (ADR-0007).
 *
 * Purely additive — one new table, no existing table touched — so every workout
 * already on the device is carried across untouched.
 *
 * The DDL must match what Room generates for
 * `WorkoutSetTombstoneEntity` exactly, including the index name, or Room's
 * post-migration schema validation fails. The generated JSON in `app/schemas/4.json`
 * is the reference.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_set_tombstone` (
                `workoutSetId` TEXT NOT NULL,
                `workoutExerciseId` TEXT NOT NULL,
                `workoutSessionId` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                PRIMARY KEY(`workoutSetId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_set_tombstone_workoutSessionId` " +
                "ON `workout_set_tombstone` (`workoutSessionId`)",
        )
    }
}

/**
 * Every migration the database ships with, passed to `addMigrations` in
 * DatabaseModule. Declared last so each migration is defined before this list
 * references it.
 */
val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
