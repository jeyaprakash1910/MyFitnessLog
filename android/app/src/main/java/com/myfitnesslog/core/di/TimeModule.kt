package com.myfitnesslog.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides a UTC [Clock]. Injecting the clock (instead of calling Instant.now()
 * directly) lets repositories stamp createdAt/updatedAt deterministically in
 * tests. All timestamps are UTC (DATABASE.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
