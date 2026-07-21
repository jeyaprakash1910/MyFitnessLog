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
 * without a matching migration will throw on a user's device. **This repository
 * has no CI**, which makes this test the only thing that catches such a change
 * before it ships. It must fail loudly rather than skip.
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
     * Every schema version between the baseline and the current one must have a
     * migration path. Today the two are the same, so this is trivially true —
     * but the moment the version is bumped without adding a `Migration`, this
     * fails, which is exactly the point.
     */
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
