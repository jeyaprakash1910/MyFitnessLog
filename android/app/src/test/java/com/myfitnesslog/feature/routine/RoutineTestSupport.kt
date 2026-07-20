package com.myfitnesslog.feature.routine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Shared helpers for routine tests: an in-memory database seeded with a couple
 * of exercises and a real [RoutineRepositoryImpl] wired to it.
 */
internal object RoutineTestData {
    val categoryId: UUID = UUID.randomUUID()
    val benchId: UUID = UUID.randomUUID()
    val squatId: UUID = UUID.randomUUID()
    val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
}

internal fun newInMemoryDatabase(): MyFitnessLogDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        MyFitnessLogDatabase::class.java,
    ).build()

internal suspend fun MyFitnessLogDatabase.seedExercises() {
    exerciseCategoryDao().upsert(ExerciseCategoryEntity(RoutineTestData.categoryId, "Legs"))
    exerciseDao().upsertAll(
        listOf(
            ExerciseEntity(RoutineTestData.benchId, RoutineTestData.categoryId, "Bench Press"),
            ExerciseEntity(RoutineTestData.squatId, RoutineTestData.categoryId, "Squat"),
        ),
    )
}

/**
 * Awaits the first value matching [predicate] on a Flow — used to wait for real
 * Room emissions (which arrive on background threads) in ViewModel integration
 * tests, instead of virtual-time advancement.
 */
internal suspend fun <T> Flow<T>.awaitFirst(
    timeoutMs: Long = 5_000,
    predicate: (T) -> Boolean,
): T = withTimeout(timeoutMs) { first(predicate) }

internal fun MyFitnessLogDatabase.newRepository(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
): RoutineRepositoryImpl =
    RoutineRepositoryImpl(
        routineDao = routineDao(),
        routineExerciseDao = routineExerciseDao(),
        ioDispatcher = dispatcher,
        clock = RoutineTestData.clock,
    )
