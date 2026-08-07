package com.myfitnesslog.core.util

import javax.inject.Qualifier

/**
 * Qualifiers for injecting specific [kotlinx.coroutines.CoroutineDispatcher]s.
 *
 * Injecting dispatchers (rather than referencing Dispatchers.IO directly) keeps
 * repositories and data sources testable: tests can substitute a
 * TestDispatcher. See docs/ANDROID_ARCHITECTURE.md, section 8.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Qualifies the process-lifetime [kotlinx.coroutines.CoroutineScope] for work
 * that must outlive any screen, such as the launch-time restore.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
