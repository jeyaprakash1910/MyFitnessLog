package com.myfitnesslog.feature.routine

import kotlinx.coroutines.runBlocking
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The teardown contract that TD-015 depends on.
 *
 * The flake was `Dispatchers.Main is used concurrently with setting it`, thrown
 * from `resetMain`. kotlinx's guard only fires on a **write**, and the reader that
 * raced it was identified on 2026-08-10 from the exception's cause, which nine
 * earlier attempts never inspected:
 *
 *     at YieldKt.yield(Yield.kt:36)
 *     at CombineKt$combineInternal$2$1$1.emit(Combine.kt:30)
 *     at androidx.room.CoroutinesRoom$Companion$execute$4$job$1
 *
 * `combine` calls `yield()` on every emission, `yield()` reads the Main delegate,
 * and because Main is unconfined the continuation resumes inline **on Room's
 * background thread**. So the read happened off the test thread and could overlap
 * the test thread's write.
 *
 * [closeAndDrain] is what makes the write safe: it cancels the collectors, closes
 * the database, and then **waits for Room's threads to terminate**. Waiting is the
 * part every earlier attempt lacked, and without it cancellation only moves the
 * race rather than removing it.
 *
 * These assertions exist because the property is otherwise invisible. A test that
 * merely passes proves nothing about whether a thread was still running when it
 * finished.
 */
@RunWith(RobolectricTestRunner::class)
class TestDatabaseTeardownTest {

    @Test
    fun `a test database runs on executors this test owns, not Room's shared pool`() {
        val database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }

        assertEquals(
            "expected a dedicated query and transaction executor. Room's default " +
                "ArchTaskExecutor pool is shared and cannot be joined, which is why " +
                "teardown could never wait for it",
            2,
            database.testExecutors().size,
        )

        database.closeAndDrain()
    }

    @Test
    fun `closeAndDrain leaves no Room thread alive to touch Dispatchers Main`() {
        val database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        val executors = database.testExecutors()
        assertTrue("precondition: the database has executors", executors.isNotEmpty())

        database.closeAndDrain()

        assertTrue(
            "closeAndDrain must wait for Room's threads. Any survivor can read the " +
                "Dispatchers.Main delegate while resetMain writes it, which is TD-015",
            executors.all { it.isTerminated },
        )
    }

    /**
     * Plain `close()` is not enough, which is the whole reason [closeAndDrain]
     * exists. Asserting the difference stops someone simplifying it away.
     */
    @Test
    fun `plain close does not wait, which is why closeAndDrain exists`() {
        val database: MyFitnessLogDatabase = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        val executors = database.testExecutors()

        database.close()

        assertFalse(
            "close() returns while Room's threads are still alive",
            executors.all { it.isTerminated },
        )

        database.closeAndDrain() // clean up properly
    }
}
