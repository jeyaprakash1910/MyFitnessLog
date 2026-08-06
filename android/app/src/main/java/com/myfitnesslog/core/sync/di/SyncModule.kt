package com.myfitnesslog.core.sync.di

import com.myfitnesslog.core.sync.SyncManager
import com.myfitnesslog.core.sync.SyncManagerImpl
import com.myfitnesslog.core.sync.SyncTrigger
import com.myfitnesslog.core.sync.engine.SyncEngine
import com.myfitnesslog.core.sync.engine.SyncEngineImpl
import com.myfitnesslog.core.sync.engine.SyncRecovery
import com.myfitnesslog.core.sync.engine.SyncRecoveryImpl
import com.myfitnesslog.core.sync.restore.RestoreManager
import com.myfitnesslog.core.sync.restore.RestoreManagerImpl
import com.myfitnesslog.core.sync.source.RoutineExerciseSyncSource
import com.myfitnesslog.core.sync.source.RoutineSyncSource
import com.myfitnesslog.core.sync.source.WorkoutExerciseSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSessionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetDeletionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetSyncSource
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the synchronization layer.
 *
 * Each `SyncSource` is bound to the repository implementation that already owns
 * that table. Both implementations are `@Singleton`-scoped classes, so the
 * repository the UI uses and the source the engine uses are the *same instance* —
 * there is one persistence path, not two.
 *
 * Nothing injects [SyncEngine] yet; the scheduling layer that invokes it arrives
 * in Phase 3.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): SyncEngine

    @Binds
    @Singleton
    abstract fun bindSyncRecovery(impl: SyncRecoveryImpl): SyncRecovery

    @Binds
    @Singleton
    abstract fun bindSyncManager(impl: SyncManagerImpl): SyncManager

    /**
     * The download half of synchronisation (ADR-0017). Singleton so a restore
     * cannot be started twice concurrently by two callers.
     */
    @Binds
    @Singleton
    abstract fun bindRestoreManager(impl: RestoreManagerImpl): RestoreManager

    /**
     * The data layer's narrow view of the same singleton: repositories can ask
     * for a sync but cannot reach the periodic schedule.
     */
    @Binds
    @Singleton
    abstract fun bindSyncTrigger(impl: SyncManagerImpl): SyncTrigger

    @Binds
    abstract fun bindRoutineSyncSource(impl: RoutineRepositoryImpl): RoutineSyncSource

    @Binds
    abstract fun bindRoutineExerciseSyncSource(
        impl: RoutineRepositoryImpl,
    ): RoutineExerciseSyncSource

    @Binds
    abstract fun bindWorkoutSessionSyncSource(
        impl: WorkoutRepositoryImpl,
    ): WorkoutSessionSyncSource

    @Binds
    abstract fun bindWorkoutExerciseSyncSource(
        impl: WorkoutRepositoryImpl,
    ): WorkoutExerciseSyncSource

    @Binds
    abstract fun bindWorkoutSetSyncSource(impl: WorkoutRepositoryImpl): WorkoutSetSyncSource

    @Binds
    abstract fun bindWorkoutSetDeletionSyncSource(
        impl: WorkoutRepositoryImpl,
    ): WorkoutSetDeletionSyncSource
}
