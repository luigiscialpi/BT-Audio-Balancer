package com.example.btaudiobalancer.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btaudiobalancer.audio.AudioEffectApplier
import com.example.btaudiobalancer.data.DeviceAudioSettings
import com.example.btaudiobalancer.data.DeviceSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val connectedDevices: List<DeviceAudioSettings> = emptyList(),
    val savedDevices: List<DeviceAudioSettings> = emptyList(),
    val hasBluetoothPermission: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DeviceSettingsRepository(app)
    private val applier    = AudioEffectApplier(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val savedDevices: StateFlow<List<DeviceAudioSettings>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            savedDevices.collect { saved ->
                _uiState.update { it.copy(savedDevices = saved) }
            }
        }
    }

    fun refreshConnectedDevices(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.update { it.copy(connectedDevices = emptyList()) }
            return
        }
        try {
            val connected = adapter.bondedDevices
                ?.filter { dev ->
                    // Filtra solo quelli effettivamente connessi (API 14+)
                    try {
                        val method = dev.javaClass.getMethod("isConnected")
                        method.invoke(dev) as? Boolean == true
                    } catch (_: Exception) {
                        true // fallback: mostra tutti i paired se non riesci a verificare
                    }
                }
                ?.map { dev ->
                    val mac  = dev.address
                    val name = try { dev.name ?: mac } catch (_: SecurityException) { mac }
                    // Leggi settings salvati se esistono, altrimenti crea entry vuota
                    val saved = savedDevices.value.find { it.deviceAddress == mac }
                    saved ?: DeviceAudioSettings(
                        deviceAddress = mac,
                        deviceName    = name
                    )
                } ?: emptyList()

            _uiState.update { it.copy(connectedDevices = connected) }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(hasBluetoothPermission = false) }
        }
    }

    fun updateAndApply(settings: DeviceAudioSettings) {
        viewModelScope.launch {
            repository.save(settings)
            applier.apply(settings)
        }
    }

    fun deleteDevice(mac: String) {
        viewModelScope.launch {
            repository.delete(mac)
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasBluetoothPermission = granted) }
    }

    override fun onCleared() {
        applier.release()
        super.onCleared()
    }
}
