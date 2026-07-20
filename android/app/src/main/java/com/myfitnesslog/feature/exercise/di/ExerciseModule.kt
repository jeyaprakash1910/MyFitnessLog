package com.myfitnesslog.feature.exercise.di

import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepositoryImpl
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepositoryImpl
import com.myfitnesslog.feature.exercise.data.remote.ExerciseApi
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Hilt wiring for the exercise feature's data layer.
 *
 * `@Binds` maps each repository interface to its implementation so consumers
 * depend only on the interface. The companion `@Provides` methods create the
 * Retrofit API interfaces from the app-wide [Retrofit] instance supplied by
 * NetworkModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExerciseModule {

    @Binds
    @Singleton
    abstract fun bindExerciseCategoryRepository(
        impl: ExerciseCategoryRepositoryImpl,
    ): ExerciseCategoryRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(
        impl: ExerciseRepositoryImpl,
    ): ExerciseRepository

    companion object {

        @Provides
        @Singleton
        fun provideExerciseCategoryApi(retrofit: Retrofit): ExerciseCategoryApi =
            retrofit.create()

        @Provides
        @Singleton
        fun provideExerciseApi(retrofit: Retrofit): ExerciseApi =
            retrofit.create()
    }
}
