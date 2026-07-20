package com.myfitnesslog.feature.workout.di

import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the workout feature. Binds the repository interface to its
 * implementation. StartWorkoutUseCase is constructor-injected and needs no
 * binding; DAOs and Clock come from DatabaseModule / TimeModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository
}
