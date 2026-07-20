package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutSessionEntity].
 *
 * `observeActive`/`getActive` support the single-active-session invariant
 * (enforced in the repository in Phase 2). `deleteById` exists to purge a
 * discarded/abandoned session and its children via cascade (DATABASE.md permits
 * deleting discarded workouts); completed history is never deleted.
 */
@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeById(id: UUID): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutSessionEntity?

    @Upsert
    suspend fun upsert(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
