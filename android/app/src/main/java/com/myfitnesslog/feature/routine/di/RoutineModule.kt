package com.myfitnesslog.feature.routine.di

import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Hilt wiring for the routine feature. Binds the repository interface to its
 * implementation; the DAOs and Clock it needs are provided by DatabaseModule and
 * TimeModule.
 *
 * The companion provides [RoutineApi] from the app-wide Retrofit instance,
 * following the pattern established by ExerciseModule. Nothing injects it yet —
 * the synchronization engine that consumes it arrives in Phase 2.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutineModule {

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    companion object {

        @Provides
        @Singleton
        fun provideRoutineApi(retrofit: Retrofit): RoutineApi = retrofit.create()
    }
}
