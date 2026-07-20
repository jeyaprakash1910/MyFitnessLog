package com.myfitnesslog.core.di

import android.content.Context
import androidx.room.Room
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs.
 *
 * The database is an application-scoped singleton. DAOs are provided from it so
 * consumers (repositories, in later phases) depend on the narrow DAO interface
 * rather than the whole database.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MyFitnessLogDatabase =
        Room.databaseBuilder(
            context,
            MyFitnessLogDatabase::class.java,
            MyFitnessLogDatabase.DATABASE_NAME,
        ).build()

    @Provides
    fun provideExerciseCategoryDao(database: MyFitnessLogDatabase): ExerciseCategoryDao =
        database.exerciseCategoryDao()

    @Provides
    fun provideExerciseDao(database: MyFitnessLogDatabase): ExerciseDao =
        database.exerciseDao()
}
