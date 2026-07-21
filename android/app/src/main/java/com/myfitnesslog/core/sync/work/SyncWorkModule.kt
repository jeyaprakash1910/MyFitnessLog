package com.myfitnesslog.core.sync.work

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the synchronization scheduling layer.
 *
 * [WorkManager] is obtained through `getInstance` rather than constructed:
 * WorkManager owns its own singleton, initialized from the Application's
 * `Configuration.Provider`. Providing it here lets [SyncSchedulerImpl] be an
 * ordinary injectable class and lets tests substitute the test instance.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncWorkModule {

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: SyncSchedulerImpl): SyncScheduler

    companion object {

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
