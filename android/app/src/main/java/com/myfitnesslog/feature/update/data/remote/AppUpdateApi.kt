package com.myfitnesslog.feature.update.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming

/**
 * Retrofit interface for the app's own distribution endpoints (ADR-0016).
 *
 * Both calls go through the same authenticated client as everything else, so the
 * `X-API-Key` header is applied by the existing interceptor.
 */
interface AppUpdateApi {

    /**
     * Returns the latest published release, or **503** when the backend has no
     * artifact store configured or cannot reach it. Wrapped in [Response] so that
     * 503 can be read as an ordinary answer instead of arriving as an exception:
     * "cannot tell right now" is an expected state here, not a fault.
     */
    @GET("app/latest-version")
    suspend fun getLatestVersion(): Response<LatestVersionDto>

    /**
     * Streams the release APK.
     *
     * [Streaming] is essential, not an optimisation: without it Retrofit buffers
     * the entire response into memory before returning, which for a
     * tens-of-megabytes APK risks an OutOfMemoryError and makes real download
     * progress impossible to report.
     */
    @Streaming
    @GET("app/apk")
    suspend fun downloadApk(): Response<ResponseBody>
}
