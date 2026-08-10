package com.myfitnesslog.core.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.myfitnesslog.BuildConfig
import com.myfitnesslog.core.network.ApiKeyInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

/**
 * Provides the Retrofit/OkHttp networking stack.
 *
 * The base URL comes from [BuildConfig.API_BASE_URL], populated at build time
 * from `apiBaseUrl` in `local.properties` and defaulting to the emulator's host
 * loopback. No development address is hardcoded here: pointing the app at a
 * LAN-hosted backend (to sync from a physical device) is a configuration change,
 * not a code change. See README "Configuring the backend URL".
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // tolerate additive backend changes
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        // Timeouts sized for the deployment, not for OkHttp's defaults.
        //
        // The backend runs on a free Render instance that spins down after
        // inactivity, so the first request after a quiet period waits for a
        // container to boot: 22s measured on 2026-08-10, and 100.8s in the deploy
        // log of 2026-08-05. OkHttp defaults every timeout to 10 seconds, so that
        // first request could not succeed. The update check is where it showed,
        // because it runs once on launch with no retry and reports "could not
        // reach the server" to the user, while background sync merely burned a
        // WorkManager attempt and recovered on the next one.
        //
        // DEPLOYMENT.md predicted exactly this and left it alone pending evidence:
        // "no client timeout/retry change until real usage shows it is needed".
        // Real usage showed it on 2026-08-10, when a phone on 1.5.0 could not see
        // the 1.6.0 release.
        builder.connectTimeout(15, TimeUnit.SECONDS)
        // Read covers the cold start. It applies between bytes rather than to the
        // whole response, so a slow APK download is not affected by the size of
        // this number.
        builder.readTimeout(120, TimeUnit.SECONDS)
        builder.writeTimeout(30, TimeUnit.SECONDS)
        // Deliberately no callTimeout. That one caps an entire call including the
        // response body, and the 8.7 MB APK for an in-app update streams through
        // this same client (ADR-0016). A ceiling generous enough for that download
        // on a poor connection would be too generous to be a useful ceiling.
        //
        // Authenticate every request first, so the X-API-Key header is present in
        // the chain (and visible to the logging interceptor below when enabled).
        builder.addInterceptor(ApiKeyInterceptor(BuildConfig.API_KEY))
        if (BuildConfig.ENABLE_NETWORK_LOGGING) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
