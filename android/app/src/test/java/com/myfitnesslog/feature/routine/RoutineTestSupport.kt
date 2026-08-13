package com.myfitnesslog.feature.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout

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

/**
 * Room's executors for each test database, so [closeAndDrain] can wait for them.
 * Emptied as databases close, so nothing accumulates across a class.
 */
private val testDatabaseExecutors =
    java.util.Collections.synchronizedMap(mutableMapOf<MyFitnessLogDatabase, List<ExecutorService>>())

/**
 * An in-memory database on executors this test owns. TD-015.
 *
 * Room's default pool comes from `ArchTaskExecutor`, is shared, and cannot be
 * joined, so there is no way to know when its threads have finished. Two dedicated
 * executors can be shut down and awaited, which is what [closeAndDrain] does and
 * what the teardown race needs.
 *
 * They must be **different objects**: `RoomDatabase.Builder.build()` copies the
 * query executor into the transaction executor when only the former is supplied
 * (RoomDatabase.kt:1252 in Room 2.6.1), and SQLite transactions are thread-bound,
 * so sharing one deadlocks. Neither may be a same-thread executor either: Room
 * posts the invalidation refresh to the query executor expecting it to run after
 * the write commits, and inline it observes the pre-commit state and never emits.
 */
internal fun newInMemoryDatabase(): MyFitnessLogDatabase {
    val query = Executors.newSingleThreadExecutor { r ->
        Thread(r, "room-test-query").apply { isDaemon = true }
    }
    val transaction = Executors.newSingleThreadExecutor { r ->
        Thread(r, "room-test-transaction").apply { isDaemon = true }
    }
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        MyFitnessLogDatabase::class.java,
    )
        .setQueryExecutor(query)
        .setTransactionExecutor(transaction)
        .build()
    testDatabaseExecutors[database] = listOf(query, transaction)
    return database
}

/**
 * Closes the database and waits for its threads to stop.
 *
 * Call this instead of `close()` from any test that installs a test Main
 * dispatcher, **after cancelling whatever was collecting**, and before
 * `Dispatchers.resetMain()`.
 *
 * The order matters and each step earns its place. Cancelling stops the
 * collectors so they never observe a closed database; draining waits for Room's
 * threads, which are the ones that read `Dispatchers.Main` while resuming a
 * `combine`; only then is it safe to write Main.
 */
/**
 * ViewModels a test constructed, so teardown can cancel their scopes.
 *
 * A ViewModel built directly is never cleared, so its `viewModelScope` outlives
 * the test and keeps collecting Room flows on `Dispatchers.Main`. Those collectors
 * are the readers in TD-015; see [closeAndDrain].
 *
 * Robolectric gives each test class its own environment and Gradle runs classes
 * sequentially within a worker, so a module-level list is safe here. It is cleared
 * on every cancel so nothing carries between tests.
 */
private val trackedViewModels = mutableListOf<ViewModel>()

/** Registers a ViewModel for cancellation at teardown. See [closeAndDrain]. */
internal fun <T : ViewModel> T.tracked(): T = also { trackedViewModels += it }

/**
 * This database's own executors, for tests that need to assert on teardown.
 *
 * Returned rather than scanned by thread name, because every test database in the
 * JVM names its threads the same way and a global scan sees other classes' too.
 */
internal fun MyFitnessLogDatabase.testExecutors(): List<ExecutorService> =
    testDatabaseExecutors[this].orEmpty()

internal fun MyFitnessLogDatabase.closeAndDrain() {
    // Cancel first: a collector that is still running when the database closes
    // observes a closed database and throws, and that exception surfaces in the
    // *next* class as UncaughtExceptionsBeforeTest. Measured on 2026-08-10.
    trackedViewModels.forEach { it.viewModelScope.cancel() }
    trackedViewModels.clear()
    close()
    testDatabaseExecutors.remove(this)?.forEach { executor ->
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}

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

/**
 * Runs a ViewModel action and waits for the work it launched to finish.
 *
 * **Use this before asserting on the database, or on anything else the action
 * writes rather than exposes.** ViewModels write through `viewModelScope.launch`
 * and return immediately, so a read taken straight afterwards is a race that a
 * fast machine wins and a loaded CI runner loses. That single pattern caused every
 * flake seen on 2026-08-12 and 2026-08-13, in four different test classes.
 *
 * [awaitFirst] already covers the other half of the problem - waiting for state
 * the action *does* expose - and the two together remove the need to sample
 * anything. The rule is: assert on state with [awaitFirst], assert on side effects
 * with this.
 *
 * It joins precisely the children the block starts, rather than draining or
 * sleeping. Children captured beforehand are excluded because `stateIn` keeps a
 * long-lived collector in the same scope, and joining that would simply hang.
 *
 * This is exact rather than heuristic: when the joined coroutines complete, their
 * writes have committed. A repository call that queues further work on Room's
 * executors stays suspended until that work returns, so the join covers it too.
 */
internal suspend fun ViewModel.awaitWork(timeoutMs: Long = 5_000, block: () -> Unit) {
    val job = viewModelScope.coroutineContext[Job]
        ?: error("viewModelScope has no Job; cannot await its work")
    val before = job.children.toSet()
    block()
    withTimeout(timeoutMs) {
        job.children.filter { it !in before }.forEach { it.join() }
    }
}

internal fun MyFitnessLogDatabase.newRepository(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
): RoutineRepositoryImpl =
    RoutineRepositoryImpl(
        syncTrigger = RecordingSyncTrigger(),
        routineDao = routineDao(),
        routineExerciseDao = routineExerciseDao(),
        ioDispatcher = dispatcher,
        clock = RoutineTestData.clock,
    )
