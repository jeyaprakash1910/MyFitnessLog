package com.myfitnesslog.feature.routine.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [RoutineExerciseEntity].
 *
 * The reactive read joins the master exercise name for display
 * ([RoutineExerciseDetail]) and excludes soft-deleted rows, ordered by
 * displayOrder. [getByRoutine] is the non-reactive read the repository uses when
 * reordering and duplicating. Mutations go through [upsert]/[upsertAll].
 */
@Dao
interface RoutineExerciseDao {

    @Query(
        """
        SELECT re.*, e.name AS exerciseName
        FROM routine_exercise re
        JOIN exercise e ON e.id = re.exerciseId
        WHERE re.routineId = :routineId AND re.isDeleted = 0
        ORDER BY re.displayOrder ASC
        """,
    )
    fun observeDetailsByRoutine(routineId: UUID): Flow<List<RoutineExerciseDetail>>

    @Query(
        "SELECT * FROM routine_exercise WHERE routineId = :routineId AND isDeleted = 0 " +
            "ORDER BY displayOrder ASC",
    )
    suspend fun getByRoutine(routineId: UUID): List<RoutineExerciseEntity>

    /**
     * Non-reactive detail read (with exercise name) used when snapshotting a
     * routine into a workout at start (Milestone 6).
     */
    @Query(
        """
        SELECT re.*, e.name AS exerciseName
        FROM routine_exercise re
        JOIN exercise e ON e.id = re.exerciseId
        WHERE re.routineId = :routineId AND re.isDeleted = 0
        ORDER BY re.displayOrder ASC
        """,
    )
    suspend fun getDetailsByRoutine(routineId: UUID): List<RoutineExerciseDetail>

    @Query("SELECT * FROM routine_exercise WHERE id = :id")
    suspend fun getById(id: UUID): RoutineExerciseEntity?

    @Upsert
    suspend fun upsert(routineExercise: RoutineExerciseEntity)

    @Upsert
    suspend fun upsertAll(routineExercises: List<RoutineExerciseEntity>)
}
