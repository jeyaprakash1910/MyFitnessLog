package com.myfitnesslog.feature.update.di

import com.myfitnesslog.BuildConfig
import com.myfitnesslog.feature.update.data.AppUpdateRepository
import com.myfitnesslog.feature.update.data.AppUpdateRepositoryImpl
import com.myfitnesslog.feature.update.data.remote.AppUpdateApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies this build's own version name, so it can be substituted in tests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InstalledVersionName

/** Hilt wiring for in-app updates (ADR-0016). */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdateApiModule {

    @Provides
    @Singleton
    fun provideAppUpdateApi(retrofit: Retrofit): AppUpdateApi =
        retrofit.create(AppUpdateApi::class.java)

    @Provides
    @InstalledVersionName
    fun provideInstalledVersionName(): String = BuildConfig.VERSION_NAME
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {

    /**
     * Bound as a singleton because the check result is shared: the banner and the
     * Settings screen observe the same state rather than each checking for itself.
     */
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository
}
