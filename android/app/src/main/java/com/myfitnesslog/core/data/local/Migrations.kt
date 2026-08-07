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
 * v4 → v5: adds `exercise_category.displayOrder` (M11 Phase 2, defect D-2).
 *
 * Additive, with a `DEFAULT 0` so existing rows get a valid value without a
 * backfill. Zero is deliberately the same for every existing row: the DAO's
 * `ORDER BY displayOrder, name` therefore degrades to the previous alphabetical
 * ordering until the next library refresh writes the real positions, so the
 * picker never enters an arbitrary intermediate order.
 *
 * The DDL must match what Room generates for `ExerciseCategoryEntity`, including
 * the `NOT NULL DEFAULT 0`, or post-migration validation fails. `app/schemas/5.json`
 * is the reference.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `exercise_category` " +
                "ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Adds the workout-exercise deletion tombstone table (TD-014).
 *
 * Mirrors `workout_set_tombstone` exactly, including the index on
 * `workoutSessionId` that lets the sync engine skip deletions belonging to a
 * session whose own upload failed.
 *
 * Additive: no existing table is touched, so nothing already recorded can be
 * lost by running it.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_exercise_tombstone` (" +
                "`workoutExerciseId` TEXT NOT NULL, " +
                "`workoutSessionId` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`workoutExerciseId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_workout_exercise_tombstone_workoutSessionId` " +
                "ON `workout_exercise_tombstone` (`workoutSessionId`)",
        )
    }
}

/**
 * Every migration the database ships with, passed to `addMigrations` in
 * DatabaseModule. Declared last so each migration is defined before this list
 * references it.
 */
val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
