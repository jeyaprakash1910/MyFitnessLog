package com.myfitnesslog.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class SettingsRepositoryImplTest {

    private lateinit var tempFile: File
    private lateinit var scope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        tempFile = File.createTempFile("settings-test", ".preferences_pb").apply { delete() }
        scope = TestScope(StandardTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { tempFile }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun defaultsToAnyWorkoutWhenNothingStored() = runTest(scope.testScheduler) {
        assertEquals(PreviousWorkoutValues.ANY_WORKOUT, repository.previousWorkoutValues.first())
    }

    @Test
    fun persistsTheSelectedStrategy() = runTest(scope.testScheduler) {
        repository.setPreviousWorkoutValues(PreviousWorkoutValues.SAME_ROUTINE)
        assertEquals(PreviousWorkoutValues.SAME_ROUTINE, repository.previousWorkoutValues.first())
    }

    @Test
    fun writtenValueSurvivesANewRepositoryOverTheSameStore() = runTest(scope.testScheduler) {
        repository.setPreviousWorkoutValues(PreviousWorkoutValues.SAME_ROUTINE)
        // A fresh repository over the same DataStore reads back the persisted value —
        // stands in for surviving an app restart.
        val reopened = SettingsRepositoryImpl(dataStore)
        assertEquals(PreviousWorkoutValues.SAME_ROUTINE, reopened.previousWorkoutValues.first())
    }
}
