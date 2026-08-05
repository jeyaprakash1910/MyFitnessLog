package com.myfitnesslog.dto.response;

import java.time.Instant;

/**
 * Network model for {@code GET /api/v1/app/latest-version}
 * (docs/API_SPECIFICATION.md).
 *
 * <p>Deliberately does <em>not</em> carry a {@code versionCode} or an
 * "updateAvailable" flag. The backend reports what the latest release <em>is</em>;
 * whether that is newer than the caller is the caller's own question, and only
 * the caller knows what it is running. Answering it here would mean the
 * MAJOR.MINOR.PATCH ordering rule lived in two codebases at once (ADR-0016).
 *
 * @param versionName  latest published version, e.g. {@code 1.2.0}
 * @param releaseNotes release notes as Markdown; may be blank
 * @param publishedAt  publication time; null if the store did not report one
 * @param sizeBytes    size of the APK served by {@code GET /api/v1/app/apk}
 */
public record AppUpdateResponse(
        String versionName,
        String releaseNotes,
        Instant publishedAt,
        long sizeBytes
) {
}
