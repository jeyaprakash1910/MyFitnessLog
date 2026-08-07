package com.myfitnesslog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myfitnesslog.core.sync.SyncManager
import com.myfitnesslog.core.sync.restore.RestoreManager
import com.myfitnesslog.core.util.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point and Hilt dependency-injection root.
 *
 * [HiltAndroidApp] triggers Hilt's code generation and creates the
 * application-level dependency container from which all other containers
 * (Activity, ViewModel, etc.) are derived.
 *
 * It also supplies WorkManager's [Configuration]. WorkManager cannot construct a
 * `@HiltWorker` itself — those have `@AssistedInject` constructors with real
 * dependencies — so it must be given Hilt's [HiltWorkerFactory]. Implementing
 * `Configuration.Provider` puts WorkManager into on-demand initialization, which
 * requires removing its default startup provider from the manifest; without that
 * removal WorkManager initializes first with the default factory and every
 * `@HiltWorker` fails to instantiate at runtime.
 */
@HiltAndroidApp
class MyFitnessLogApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var restoreManager: RestoreManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Registers the recurring sync and requests an immediate pass for
        // anything the previous run left pending. Fire-and-forget: this only
        // enqueues work, so app startup is never delayed by it.
        syncManager.onAppStart()

        // Rebuild this device's data from the backend if it has none of its own
        // (ADR-0017). Launched rather than awaited: onCreate blocks the first
        // frame, and a restore does network work against an instance that may
        // need to wake up first. The screen appears immediately and fills in.
        //
        // It is safe to run unconditionally because it returns immediately when
        // the database is not empty, which is every launch after the first.
        applicationScope.launch { restoreManager.restoreIfEmpty() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
