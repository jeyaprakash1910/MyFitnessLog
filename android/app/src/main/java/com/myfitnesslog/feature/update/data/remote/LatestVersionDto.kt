package com.myfitnesslog.feature.update.data.remote

import kotlinx.serialization.Serializable

/**
 * Network model for `GET /api/v1/app/latest-version`
 * (docs/API_SPECIFICATION.md).
 *
 * Every field except [versionName] is defaulted: release notes, a publication
 * timestamp and a size are all presentational, and an older or partially
 * configured backend that omits one should still let the version comparison
 * happen. [versionName] has no sensible default - without it there is nothing to
 * compare - so it stays required and a response lacking it fails to deserialize.
 */
@Serializable
data class LatestVersionDto(
    val versionName: String,
    val releaseNotes: String = "",
    /** ISO-8601 instant; kept as a String because nothing yet renders it. */
    val publishedAt: String? = null,
    val sizeBytes: Long = 0L,
)
