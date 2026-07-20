package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutSetEntity]. Reads are ordered by setNumber. `deleteById`
 * supports the "delete set" action during an in-progress workout.
 */
@Dao
interface WorkoutSetDao {

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    fun observeByExercise(workoutExerciseId: UUID): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    suspend fun getByExercise(workoutExerciseId: UUID): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutSetEntity?

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity)

    @Upsert
    suspend fun upsertAll(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
