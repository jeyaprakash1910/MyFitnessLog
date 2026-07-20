package com.myfitnesslog.feature.routine.di

import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the routine feature. Binds the repository interface to its
 * implementation; the DAOs and Clock it needs are provided by DatabaseModule and
 * TimeModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutineModule {

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository
}
