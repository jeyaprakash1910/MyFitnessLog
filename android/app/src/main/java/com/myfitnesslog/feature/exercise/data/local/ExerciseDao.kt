package com.myfitnesslog.feature.exercise.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data-access object for [ExerciseEntity].
 *
 * Same conventions as [ExerciseCategoryDao]: Flow reads, suspend upserts, and
 * only the operations required today. Category-filtered and search queries are
 * deferred to Milestone 4 when the screens that consume them exist (YAGNI).
 */
@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observeById(id: UUID): Flow<ExerciseEntity?>

    /**
     * Reactive, case-insensitive, deterministically ordered filtered read.
     *
     * A single nullable-parameter query serves all filter combinations — no
     * filter, category-only, search-only, and both — so there is exactly one
     * query to maintain and the ViewModel never has to choose between flows.
     * A null/blank argument disables that predicate. `LIKE` is case-insensitive
     * for ASCII; `COLLATE NOCASE` makes that explicit.
     */
    @Query(
        """
        SELECT * FROM exercise
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR name LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY name ASC
        """,
    )
    fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    /**
     * Removes catalogue exercises the backend no longer serves.
     *
     * Reference data is server-authoritative (ADR-0003), so the local copy must
     * converge on the server's set — otherwise a withdrawn exercise lingers in
     * the picker forever, which is why the V6 migration had to rename a category
     * in place rather than remove it (M11 Phase 1, defect D-3).
     *
     * **Rows still referenced are deliberately kept.** `RoutineExercise` and
     * `WorkoutExercise` hold RESTRICT foreign keys to this table, so deleting a
     * referenced row would either fail outright or, with a different cascade
     * rule, destroy workout history. A withdrawn exercise that appears in a past
     * workout is part of that immutable record (ADR-0001) and must remain
     * resolvable. It simply stops being offered for new work once the catalogue
     * drops it.
     */
    @Query(
        """
        DELETE FROM exercise
        WHERE id NOT IN (:serverIds)
          AND id NOT IN (SELECT exerciseId FROM routine_exercise)
          AND id NOT IN (SELECT exerciseId FROM workout_exercise)
        """,
    )
    suspend fun deleteMissing(serverIds: List<UUID>): Int
}
