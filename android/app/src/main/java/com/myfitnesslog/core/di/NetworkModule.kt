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
