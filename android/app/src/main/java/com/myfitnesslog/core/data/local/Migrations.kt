package com.myfitnesslog.core.data.local

import androidx.room.migration.Migration

/**
 * Room migrations for [MyFitnessLogDatabase].
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
 * ## Why this list is empty
 *
 * Version 3 is the current schema and the baseline for real use. Versions 1 and 2
 * existed only during Milestones 3–6, on developer machines that were being
 * destructively upgraded anyway. Writing 1→2 and 2→3 migrations now would be
 * theatre: no installation that needs them exists, and untested migration code is
 * worse than none.
 *
 * A developer holding a v1 or v2 database from that era will get the loud failure
 * described above and should reinstall the app.
 *
 * The first real entry will be `MIGRATION_3_4`.
 */
val MIGRATIONS: Array<Migration> = emptyArray()
