package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutExerciseEntity]. Reads are ordered by exerciseOrder. No join
 * for the exercise name is needed — it is snapshotted onto the row.
 */
@Dao
interface WorkoutExerciseDao {

    @Query("SELECT * FROM workout_exercise WHERE workoutSessionId = :sessionId ORDER BY exerciseOrder ASC")
    fun observeBySession(sessionId: UUID): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise WHERE workoutSessionId = :sessionId ORDER BY exerciseOrder ASC")
    suspend fun getBySession(sessionId: UUID): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutExerciseEntity?

    @Upsert
    suspend fun upsert(workoutExercise: WorkoutExerciseEntity)

    @Upsert
    suspend fun upsertAll(workoutExercises: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
