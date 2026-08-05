package com.myfitnesslog.service;

import java.time.Instant;

/**
 * The latest published Android release, as resolved from the artifact store.
 *
 * <p>{@code apkAssetUrl} is an <em>API</em> URL, not a browser one: for a private
 * repository it requires the backend's credential and is useless to a client, so
 * it never leaves the server. {@link AppUpdateService#openApk()} is the only way
 * out, and it hands over bytes rather than a URL.
 *
 * @param versionName  semantic version taken from the release tag, without the
 *                     leading {@code v} (e.g. {@code 1.2.0}). The client compares
 *                     this against its own {@code BuildConfig.VERSION_NAME}; see
 *                     ADR-0016 for why the comparison lives there and not here.
 * @param releaseNotes the release body, verbatim Markdown, possibly blank
 * @param publishedAt  when the release was published
 * @param sizeBytes    APK size, so the client can show a real download size
 * @param apkFileName  the asset's own file name, used for the download filename
 * @param apkAssetUrl  server-side-only URL from which the APK bytes are fetched
 */
public record AppRelease(
        String versionName,
        String releaseNotes,
        Instant publishedAt,
        long sizeBytes,
        String apkFileName,
        String apkAssetUrl
) {
}
