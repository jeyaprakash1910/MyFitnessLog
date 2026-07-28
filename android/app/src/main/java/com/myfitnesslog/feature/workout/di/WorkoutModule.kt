package com.myfitnesslog.feature.workout.di

import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.data.remote.WorkoutLogApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import com.myfitnesslog.feature.workout.domain.RestTimer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        /**
         * The rest countdown is app-scoped, not ViewModel-scoped, so it survives
         * navigating away from the workout screen (which destroys the screen's
         * `WorkoutViewModel`) and keeps ticking in real time — the bar shows the
         * correct remaining time on return. Only process death resets it (INV-7;
         * losing it there is acceptable — no foreground service/alarm in scope). Its
         * scope is intentionally never cancelled: it lives for the whole app session.
         */
        @Provides
        @Singleton
        fun provideRestTimer(): RestTimer =
            RestTimer(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

        @Provides
        @Singleton
        fun provideWorkoutSessionApi(retrofit: Retrofit): WorkoutSessionApi = retrofit.create()

        @Provides
        @Singleton
        fun provideWorkoutLogApi(retrofit: Retrofit): WorkoutLogApi = retrofit.create()
    }
}
