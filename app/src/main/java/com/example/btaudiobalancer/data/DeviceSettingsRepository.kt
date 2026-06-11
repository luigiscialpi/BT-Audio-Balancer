package com.example.btaudiobalancer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "device_settings")

class DeviceSettingsRepository(private val context: Context) {

    private fun balanceKey(mac: String) = floatPreferencesKey("${mac}_balance")
    private fun boostKey(mac: String)   = intPreferencesKey("${mac}_boost")
    private fun nameKey(mac: String)    = stringPreferencesKey("${mac}_name")
    private fun enabledKey(mac: String) = booleanPreferencesKey("${mac}_enabled")

    /** Flusso con tutti i device salvati. */
    val allDevices: Flow<List<DeviceAudioSettings>> = context.dataStore.data.map { prefs ->
        prefs.asMap().keys
            .mapNotNull { key ->
                val raw = key.name
                if (raw.endsWith("_name")) raw.removeSuffix("_name") else null
            }
            .distinct()
            .map { mac ->
                DeviceAudioSettings(
                    deviceAddress = mac,
                    deviceName    = prefs[nameKey(mac)] ?: mac,
                    balance       = prefs[balanceKey(mac)] ?: 0f,
                    boostMb       = prefs[boostKey(mac)] ?: 0,
                    enabled       = prefs[enabledKey(mac)] ?: true
                )
            }
    }

    /** Flusso per un singolo dispositivo. */
    fun deviceFlow(mac: String): Flow<DeviceAudioSettings?> =
        context.dataStore.data.map { prefs ->
            if (prefs[nameKey(mac)] == null) null
            else DeviceAudioSettings(
                deviceAddress = mac,
                deviceName    = prefs[nameKey(mac)] ?: mac,
                balance       = prefs[balanceKey(mac)] ?: 0f,
                boostMb       = prefs[boostKey(mac)] ?: 0,
                enabled       = prefs[enabledKey(mac)] ?: true
            )
        }

    /** Salva (o aggiorna) le impostazioni di un device. */
    suspend fun save(settings: DeviceAudioSettings) {
        context.dataStore.edit { prefs ->
            prefs[nameKey(settings.deviceAddress)]    = settings.deviceName
            prefs[balanceKey(settings.deviceAddress)] = settings.balance
            prefs[boostKey(settings.deviceAddress)]   = settings.boostMb
            prefs[enabledKey(settings.deviceAddress)] = settings.enabled
        }
    }

    /** Rimuove le impostazioni di un device. */
    suspend fun delete(mac: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(nameKey(mac))
            prefs.remove(balanceKey(mac))
            prefs.remove(boostKey(mac))
            prefs.remove(enabledKey(mac))
        }
    }

    /** Legge una volta sola (suspend). */
    suspend fun getOnce(mac: String): DeviceAudioSettings? =
        deviceFlow(mac).firstOrNull()
}
