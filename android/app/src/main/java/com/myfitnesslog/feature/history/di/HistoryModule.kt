package com.myfitnesslog.feature.history.di

import com.myfitnesslog.feature.history.data.WorkoutHistoryRepository
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the history feature. Binds the read-only repository interface
 * to its implementation. The WorkoutHistoryDao is provided from DatabaseModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutHistoryRepository(
        impl: WorkoutHistoryRepositoryImpl,
    ): WorkoutHistoryRepository
}
