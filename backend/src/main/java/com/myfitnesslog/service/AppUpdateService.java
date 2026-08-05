package com.myfitnesslog.service;

import com.myfitnesslog.exception.AppUpdateUnavailableException;

import java.io.InputStream;

/**
 * Serves the Android app's own update artifact.
 *
 * <p>This is the one service that is not about workout data. It exists because
 * the app is distributed as an APK outside any store (ADR-0016), so something has
 * to tell an installed build that a newer one exists and then hand it the bytes.
 * The backend is the natural place: the phone already trusts it and already
 * authenticates to it (ADR-0013), so no second credential or second host is
 * introduced.
 */
public interface AppUpdateService {

    /**
     * The latest published release.
     *
     * @throws AppUpdateUnavailableException if updates are not configured, the
     *                                       artifact store cannot be reached, or
     *                                       the latest release carries no APK
     */
    AppRelease getLatestRelease();

    /**
     * Opens the APK bytes of {@link #getLatestRelease()} for streaming.
     *
     * <p>The caller owns the stream and must close it.
     *
     * @throws AppUpdateUnavailableException on the same conditions as
     *                                       {@link #getLatestRelease()}
     */
    InputStream openApk();
}
