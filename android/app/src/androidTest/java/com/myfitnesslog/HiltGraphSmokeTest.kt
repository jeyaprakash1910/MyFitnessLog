package com.myfitnesslog

import com.myfitnesslog.core.util.IoDispatcher
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Verifies the Hilt object graph builds and can satisfy the dependencies wired
 * in Phase 1 (dispatchers and the networking stack). This is the runtime
 * verification for "Hilt initializes" and "DI functions correctly".
 */
@HiltAndroidTest
class HiltGraphSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var retrofit: Retrofit

    @Test
    fun graphResolvesPhase1Dependencies() {
        hiltRule.inject()
        assertNotNull(ioDispatcher)
        assertNotNull(okHttpClient)
        assertNotNull(retrofit)
    }
}
