package com.myfitnesslog.core.di

import com.myfitnesslog.core.util.ApplicationScope
import com.myfitnesslog.core.util.DefaultDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * A coroutine scope that lives as long as the process.
 *
 * Work started at app launch outlives any one screen, so it cannot hang off a
 * ViewModel: a restore begun on the Home screen must not be cancelled because the
 * user immediately opened Settings. This scope is never cancelled, which is the
 * point and also the reason to use it sparingly.
 *
 * [SupervisorJob] so one failed child cannot take down the others, and an
 * injected dispatcher rather than a hardcoded one so tests can substitute their
 * own (docs/ANDROID_ARCHITECTURE.md §8).
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
