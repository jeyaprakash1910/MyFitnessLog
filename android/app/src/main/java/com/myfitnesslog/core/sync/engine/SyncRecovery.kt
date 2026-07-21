package com.myfitnesslog.core.sync.engine

import com.myfitnesslog.core.sync.source.RoutineSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSessionSyncSource
import com.myfitnesslog.core.util.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Releases synchronization claims stranded by a process that died mid-pass.
 *
 * The engine marks a row SYNCING before uploading it, and the pending queries
 * exclude SYNCING so a second pass cannot upload the same row twice. Nothing
 * clears that claim if the process is killed in between, so without recovery the
 * row would be permanently invisible to synchronization — silently never
 * uploaded. Running this before every pass is what makes the claim safe to take.
 *
 * Kept separate from [SyncEngine] rather than folded into `sync()` so that the
 * Worker's two responsibilities read explicitly at the call site, and so a test
 * can drive recovery without performing a sync.
 */
interface SyncRecovery {

    /**
     * Returns every SYNCING row to PENDING and reports how many were reclaimed.
     *
     * Idempotent: running it twice reclaims nothing the second time. Safe to call
     * when no rows are stranded, which is the normal case.
     */
    suspend fun recoverStaleSyncing(): Int
}

/**
 * Default [SyncRecovery].
 *
 * It depends on only two sources because of how the repositories implement them:
 * `RoutineRepositoryImpl` recovers both routine tables in one call, and
 * `WorkoutRepositoryImpl` recovers all three workout tables. Injecting all five
 * interfaces would resolve to the same two singletons and recover each table
 * several times over — harmless, but it would make the returned count meaningless.
 */
class SyncRecoveryImpl @Inject constructor(
    private val routineSource: RoutineSyncSource,
    private val workoutSource: WorkoutSessionSyncSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SyncRecovery {

    override suspend fun recoverStaleSyncing(): Int = withContext(ioDispatcher) {
        routineSource.recoverStaleSyncing() + workoutSource.recoverStaleSyncing()
    }
}
