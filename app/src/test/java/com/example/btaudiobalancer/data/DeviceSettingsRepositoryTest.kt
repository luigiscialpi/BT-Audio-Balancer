package com.example.btaudiobalancer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo by lazy {
        DeviceSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") }
        )
    }

    private val a = DeviceAudioSettings("AA:AA:AA:AA:AA:AA", "Cuffie", balance = -0.5f, boostMb = 300)
    private val b = DeviceAudioSettings("BB:BB:BB:BB:BB:BB", "Auto", balance = 0.8f, enabled = false)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun savedSettingsAreReadBackForTheSameDevice() = runBlocking<Unit> {
        repo.save(a)
        assertEquals(a, repo.getOnce(a.deviceAddress))
    }

    @Test
    fun unknownDeviceReturnsNull() = runBlocking<Unit> {
        repo.save(a)
        assertNull(repo.getOnce(b.deviceAddress))
    }

    @Test
    fun eachDeviceKeepsItsOwnSettings() = runBlocking<Unit> {
        repo.save(a)
        repo.save(b)
        val changed = a.copy(balance = 1f, boostMb = 1500)
        repo.save(changed)
        assertEquals(changed, repo.getOnce(a.deviceAddress))
        assertEquals(b, repo.getOnce(b.deviceAddress))
    }

    @Test
    fun savingTwiceUpdatesInsteadOfDuplicating() = runBlocking<Unit> {
        repo.save(a)
        repo.save(a.copy(boostMb = 900))
        assertEquals(listOf(a.copy(boostMb = 900)), repo.allDevices.first())
    }

    @Test
    fun deleteRemovesOnlyThatDevice() = runBlocking<Unit> {
        repo.save(a)
        repo.save(b)
        repo.delete(a.deviceAddress)
        assertNull(repo.getOnce(a.deviceAddress))
        assertEquals(listOf(b), repo.allDevices.first())
    }
}
