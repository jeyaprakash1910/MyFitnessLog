package com.myfitnesslog.feature.update.domain

/**
 * A MAJOR.MINOR.PATCH version, comparable against another.
 *
 * This exists so "is the backend's latest release newer than this build?" is
 * answered by ordered numeric fields rather than by string comparison, which gets
 * `"1.10.0" < "1.9.0"` wrong - a mistake that would silently stop offering updates
 * at the first double-digit minor and look like the feature simply not working.
 *
 * The comparison lives on the client, not the backend: only the client knows what
 * it is running, and putting the rule here keeps the ordering logic in one place
 * rather than in both codebases (ADR-0016). It deliberately mirrors the
 * `versionCode` formula in `app/build.gradle.kts`, which encodes the same
 * precedence - MINOR and PATCH below 100 - so the two can never disagree about
 * which of two releases is newer.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        /**
         * Parses a version name, tolerating a leading `v`, or returns null.
         *
         * Null rather than throwing: the inputs are a build constant and a network
         * response, and an unparseable one means "cannot tell whether an update
         * exists". That is a reason to stay quiet, not to crash the app - so the
         * caller is forced to handle it as an ordinary outcome.
         */
        fun parseOrNull(versionName: String?): AppVersion? {
            val match = PATTERN.matchEntire(versionName?.trim().orEmpty()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
