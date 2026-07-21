package com.myfitnesslog.feature.workout.di

import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.data.remote.WorkoutLogApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Hilt wiring for the workout feature. Binds the repository interface to its
 * implementation. StartWorkoutUseCase is constructor-injected and needs no
 * binding; DAOs and Clock come from DatabaseModule / TimeModule.
 *
 * The companion provides the two workout upload APIs from the app-wide Retrofit
 * instance. Nothing injects them yet — the synchronization engine that consumes
 * them arrives in Phase 2.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    companion object {

        @Provides
        @Singleton
        fun provideWorkoutSessionApi(retrofit: Retrofit): WorkoutSessionApi = retrofit.create()

        @Provides
        @Singleton
        fun provideWorkoutLogApi(retrofit: Retrofit): WorkoutLogApi = retrofit.create()
    }
}
