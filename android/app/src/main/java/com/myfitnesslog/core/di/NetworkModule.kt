package com.myfitnesslog.core.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.myfitnesslog.BuildConfig
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

/**
 * Provides the Retrofit/OkHttp networking stack.
 *
 * Phase 1 wires the client and JSON converter but declares **no API interfaces
 * and makes no network calls** — the UI reads exclusively from Room (ADR-0002).
 * The stack exists so the graph is complete and Phase 2 can add API interfaces
 * without touching this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Development base URL from docs/API_SPECIFICATION.md. 10.0.2.2 is the
    // host loopback as seen from the Android emulator.
    private const val BASE_URL = "http://10.0.2.2:8080/api/v1/"

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
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
