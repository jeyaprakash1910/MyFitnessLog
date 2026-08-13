package com.myfitnesslog.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the database migration policy.
 *
 * The app no longer falls back to a destructive upgrade, so a schema change
 * without a matching migration will throw on a user's device. This is the only
 * thing that catches such a change before it ships, so it must fail loudly
 * rather than skip.
 *
 * It runs on every push, in the `android-instrumented` job in
 * `.github/workflows/ci.yml`, on an emulator. That job exists because of this
 * test: for a while it lived here and nothing ever executed it, and on
 * 2026-08-07 a migration with a wrong column type reached main and had to be
 * caught by hand.
 *
 * When the schema changes:
 *  1. bump the version and let Room export the new JSON into `app/schemas/`,
 *  2. add a `Migration` to `MIGRATIONS`,
 *  3. add a case here that seeds realistic rows at the old version, migrates,
 *     and asserts the data survived — not merely that the migration ran.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"

        /**
         * The oldest schema a real installation can have.
         *
         * Versions 1 and 2 existed only during Milestones 3–6, on developer
         * machines that were being destructively upgraded anyway, so no
         * migration path into them is provided (see [MIGRATIONS]).
         */
        const val BASELINE_VERSION = 3
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyFitnessLogDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** Directory of exported schema JSONs, mounted as test-APK assets. */
    private val schemaDir: String get() = MyFitnessLogDatabase::class.java.canonicalName!!

    /**
     * Exported schema versions present in `app/schemas/`.
     *
     * Read from the **instrumentation** context: the schemas are assets of the
     * test APK, not of the app under test, so `ApplicationProvider` cannot see
     * them.
     */
    private fun exportedSchemaVersions(): List<Int> =
        InstrumentationRegistry.getInstrumentation().context.assets
            .list(schemaDir)
            .orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .sorted()

    /**
     * The schema version the app would actually create right now, read from
     * SQLite's `user_version` on a throwaway database.
     *
     * Deliberately **not** derived from the exported schema assets: those are
     * packaged into the test APK by an asset-merge step that runs *before* Room
     * exports the new JSON, so on the build that introduces a version bump the
     * assets still describe the previous version. A guard that only fires on the
     * second build is not a guard. (Reflection is not an option either — Room's
     * `@Database` annotation is not retained at runtime.)
     */
    private fun currentSchemaVersion(): Int {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val probeDb = "schema-version-probe.db"
        context.deleteDatabase(probeDb)
        val database = Room.databaseBuilder(context, MyFitnessLogDatabase::class.java, probeDb)
            .addMigrations(*MIGRATIONS)
            .build()
        val version = database.openHelper.readableDatabase.version
        database.close()
        context.deleteDatabase(probeDb)
        return version
    }

    /**
     * The exported schema for the current version must exist. Without it Room
     * cannot validate migrations at all, and the guarantee this test provides
     * would quietly become vacuous.
     */
    @Test
    fun currentSchemaIsExportedAndCommitted() {
        val versions = exportedSchemaVersions()

        assertTrue(
            "No exported schemas found in assets/$schemaDir. Room's schemaLocation " +
                "export must be committed, or migrations cannot be validated.",
            versions.isNotEmpty(),
        )
        assertTrue(
            "Expected the v$BASELINE_VERSION baseline schema among $versions.",
            BASELINE_VERSION in versions,
        )
    }

    /**
     * A database created at the baseline version opens with the real Room
     * configuration — the same one the app uses, with no destructive fallback —
     * and its data is still there afterwards.
     *
     * This is the assertion that would have caught the old
     * `fallbackToDestructiveMigration()` behaviour: under that configuration a
     * mismatched schema silently recreated the database, and the seeded row
     * would have vanished rather than failing.
     */
    @Test
    fun baselineDatabaseOpensWithoutLosingData() {
        val categoryId = "10000000-0000-0000-0000-000000000001"

        helper.createDatabase(TEST_DB, BASELINE_VERSION).use { db ->
            db.execSQL(
                "INSERT INTO exercise_category (id, name) VALUES (?, ?)",
                arrayOf(categoryId, "Chest"),
            )
        }

        // Open with the production configuration, including MIGRATIONS.
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(*MIGRATIONS)
            .build()

        val categories = database.openHelper.readableDatabase
            .query("SELECT id, name FROM exercise_category")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
        database.close()

        assertEquals(listOf(categoryId to "Chest"), categories)
    }

    /**
     * v3 → v4 (adds `workout_set_tombstone`, ADR-0007) preserves a logged
     * workout.
     *
     * The seeded rows are a real session → exercise → set chain rather than a
     * token row, because that chain is the data a user would actually lose. The
     * assertion is that it survived, not merely that the migration executed.
     */
    @Test
    fun migration3To4PreservesLoggedWorkouts() {
        val sessionId = "20000000-0000-0000-0000-000000000002"
        val exerciseId = "20000000-0000-0000-0000-000000000003"
        val catalogueExerciseId = "20000000-0000-0000-0000-000000000004"
        val categoryId = "20000000-0000-0000-0000-000000000005"
        val setId = "20000000-0000-0000-0000-000000000006"

        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO exercise_category (id, name) VALUES (?, ?)",
                arrayOf(categoryId, "Chest"),
            )
            db.execSQL(
                "INSERT INTO exercise (id, categoryId, name) VALUES (?, ?, ?)",
                arrayOf(catalogueExerciseId, categoryId, "Bench Press"),
            )
            db.execSQL(
                "INSERT INTO workout_session " +
                    "(id, routineId, status, startedAt, endedAt, notes, createdAt, updatedAt, syncStatus) " +
                    "VALUES (?, NULL, 'COMPLETED', 1000, 2000, NULL, 1000, 2000, 'PENDING')",
                arrayOf(sessionId),
            )
            db.execSQL(
                "INSERT INTO workout_exercise " +
                    "(id, workoutSessionId, exerciseId, exerciseName, exerciseOrder, targetSets, " +
                    "minTargetReps, maxTargetReps, targetRestSeconds, notes, createdAt, updatedAt, syncStatus) " +
                    "VALUES (?, ?, ?, 'Bench Press', 0, 3, 8, 12, 90, NULL, 1000, 1000, 'PENDING')",
                arrayOf(exerciseId, sessionId, catalogueExerciseId),
            )
            db.execSQL(
                "INSERT INTO workout_set " +
                    "(id, workoutExerciseId, setNumber, weight, repetitions, setCategory, " +
                    "startedAt, finishedAt, rpe, rir, isCompleted, createdAt, updatedAt, syncStatus) " +
                    "VALUES (?, ?, 1, '80.00', 8, 'WORKING', 1000, 1100, NULL, NULL, 1, 1000, 1000, 'PENDING')",
                arrayOf(setId, exerciseId),
            )
        }

        // runMigrationsAndValidate also proves the hand-written DDL matches the
        // schema Room expects — a mismatch here is what crashes on a real device.
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT weight, repetitions FROM workout_set WHERE id = '$setId'").use { c ->
                assertTrue("The logged set did not survive the migration.", c.moveToFirst())
                assertEquals("80.00", c.getString(0))
                assertEquals(8, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM workout_set_tombstone").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("The new table should start empty.", 0, c.getInt(0))
            }
        }
    }

    /**
     * v4 → v5 (adds `exercise_category.displayOrder`, M11 Phase 2) preserves the
     * catalogue and the workouts that reference it.
     *
     * The column is added with `DEFAULT 0`, so this also asserts the deliberate
     * consequence: every existing row gets position 0, which makes the DAO's
     * `ORDER BY displayOrder, name` fall back to alphabetical until the next
     * library refresh writes real positions — never an arbitrary order.
     */
    @Test
    fun migration4To5PreservesCatalogueAndDefaultsDisplayOrder() {
        val chest = "30000000-0000-0000-0000-000000000001"
        val back = "30000000-0000-0000-0000-000000000002"
        val exercise = "30000000-0000-0000-0000-000000000003"

        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO exercise_category (id, name) VALUES (?, ?), (?, ?)",
                arrayOf(chest, "Chest", back, "Back"),
            )
            db.execSQL(
                "INSERT INTO exercise (id, categoryId, name) VALUES (?, ?, ?)",
                arrayOf(exercise, chest, "Bench Press"),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT id, name, displayOrder FROM exercise_category ORDER BY name").use { c ->
                assertTrue("Categories did not survive the migration.", c.moveToFirst())
                assertEquals("Back", c.getString(1))
                assertEquals(0, c.getInt(2))
                assertTrue(c.moveToNext())
                assertEquals("Chest", c.getString(1))
                assertEquals(0, c.getInt(2))
            }
            db.query("SELECT name FROM exercise WHERE id = '$exercise'").use { c ->
                assertTrue("The exercise did not survive the migration.", c.moveToFirst())
                assertEquals("Bench Press", c.getString(0))
            }
        }
    }

    /**
     * Every schema version between the baseline and the current one must have a
     * migration path. The moment the version is bumped without adding a
     * `Migration`, this fails, which is exactly the point.
     */
    @Test
    fun migration5To6AddsExerciseTombstonesAndKeepsLoggedWork() {
        val sessionId = "40000000-0000-0000-0000-000000000001"

        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO workout_session " +
                    "(id, routineId, status, startedAt, endedAt, notes, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('$sessionId', NULL, 'IN_PROGRESS', 1, NULL, NULL, 1, 1, 'SYNCED')",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
            // The new table exists and accepts a tombstone.
            db.execSQL(
                "INSERT INTO workout_exercise_tombstone " +
                    "(workoutExerciseId, workoutSessionId, deletedAt) " +
                    "VALUES ('40000000-0000-0000-0000-000000000002', '$sessionId', 5)",
            )
            db.query("SELECT COUNT(*) FROM workout_exercise_tombstone").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }

            // Additive means additive: nothing recorded before the upgrade is lost.
            db.query("SELECT id FROM workout_session").use { c ->
                assertTrue("The existing session did not survive the migration.", c.moveToFirst())
                assertEquals(sessionId, c.getString(0))
            }
        }
    }

    /**
     * v6 to v7 adds the routine-name snapshot without disturbing recorded work.
     *
     * The column is deliberately left null on existing rows rather than backfilled
     * from the routine table. Backfilling would stamp today's name onto workouts
     * that predate the snapshot, inventing a record that was never taken, which is
     * the exact thing snapshotting exists to prevent (ADR-0004).
     */
    @Test
    fun migration6To7AddsTheRoutineNameSnapshotAndKeepsExistingSessions() {
        val sessionId = "50000000-0000-0000-0000-000000000001"

        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO workout_session " +
                    "(id, routineId, status, startedAt, endedAt, notes, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('$sessionId', NULL, 'COMPLETED', 1, 2, NULL, 1, 1, 'SYNCED')",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT id, routineName FROM workout_session").use { c ->
                assertTrue("The existing session did not survive the migration.", c.moveToFirst())
                assertEquals(sessionId, c.getString(0))
                assertTrue(
                    "A pre-v7 session must have no routine name, not an invented one.",
                    c.isNull(1),
                )
            }

            // And the column accepts a snapshot for workouts started after the upgrade.
            db.execSQL(
                "INSERT INTO workout_session " +
                    "(id, routineId, routineName, status, startedAt, endedAt, notes, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('50000000-0000-0000-0000-000000000002', NULL, 'Push', 'COMPLETED', 3, 4, NULL, 3, 3, 'PENDING')",
            )
            db.query("SELECT routineName FROM workout_session WHERE routineName IS NOT NULL").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Push", c.getString(0))
            }
        }
    }

    @Test
    fun everyVersionAboveTheBaselineHasAMigration() {
        val currentVersion = currentSchemaVersion()

        val covered = MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val missing = (BASELINE_VERSION until currentVersion)
            .map { it to it + 1 }
            .filterNot { it in covered }

        assertTrue(
            "Schema is at v$currentVersion but no migration covers $missing. " +
                "Add a Migration to MIGRATIONS and a data-preservation test here — " +
                "without one, upgrading users will crash on launch.",
            missing.isEmpty(),
        )
    }

}
