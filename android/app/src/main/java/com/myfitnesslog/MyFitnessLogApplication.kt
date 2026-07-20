package com.myfitnesslog

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt dependency-injection root.
 *
 * [HiltAndroidApp] triggers Hilt's code generation and creates the
 * application-level dependency container from which all other containers
 * (Activity, ViewModel, etc.) are derived.
 */
@HiltAndroidApp
class MyFitnessLogApplication : Application()
