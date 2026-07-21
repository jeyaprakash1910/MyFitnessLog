package com.myfitnesslog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myfitnesslog.core.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
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

    override fun onCreate() {
        super.onCreate()
        // Registers the recurring sync and requests an immediate pass for
        // anything the previous run left pending. Fire-and-forget: this only
        // enqueues work, so app startup is never delayed by it.
        syncManager.onAppStart()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
