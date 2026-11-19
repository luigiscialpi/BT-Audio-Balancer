package com.example.btaudiobalancer.ui

import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btaudiobalancer.bluetooth.displayName
import com.example.btaudiobalancer.bluetooth.queryConnectedA2dp
import com.example.btaudiobalancer.data.DeviceAudioSettings
import com.example.btaudiobalancer.data.DeviceSettingsRepository
import com.example.btaudiobalancer.service.AudioEffectService
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

/**
 * ViewModel principale dell'app.
 *
 * Non possiede piu' un AudioEffectApplier proprio: delega l'applicazione
 * degli effetti al AudioEffectService tramite Intent (ACTION_UPDATE).
 * Questo evita la duplicazione di istanze che si sovrascrivono a vicenda.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DeviceSettingsRepository(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val savedDevices: StateFlow<List<DeviceAudioSettings>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Il sistema avvisa quando un device A2DP si connette o si disconnette e quando il Bluetooth cambia stato. */
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshConnectedDevices()
    }

    init {
        viewModelScope.launch {
            savedDevices.collect { saved ->
                _uiState.update { it.copy(savedDevices = saved) }
            }
        }
        // Solo broadcast protetti di sistema: non serve il flag di export (anche su Android 14+)
        app.registerReceiver(connectionReceiver, IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(connectionReceiver)
    }

    fun refreshConnectedDevices() {
        getApplication<Application>().queryConnectedA2dp { devices ->
            val connected = devices.orEmpty().map { dev ->
                // Impostazioni salvate se esistono, altrimenti una scheda vuota
                savedDevices.value.find { it.deviceAddress == dev.address }
                    ?: DeviceAudioSettings(deviceAddress = dev.address, deviceName = dev.displayName())
            }
            _uiState.update { it.copy(connectedDevices = connected) }
        }
    }

    /**
     * Salva le impostazioni nel repository e delega l'applicazione
     * degli effetti audio al AudioEffectService tramite ACTION_UPDATE.
     *
     * Il Service applica gli effetti solo se il device corrisponde
     * a quello attualmente attivo (ultimo connesso), evitando
     * il cross-device leak.
     */
    fun updateAndApply(settings: DeviceAudioSettings) {
        viewModelScope.launch {
            repository.save(settings)
        }
        // Un profilo di un device non connesso si salva e basta: applicarlo muoverebbe
        // gli effetti di sistema per un device che non sta riproducendo nulla.
        if (_uiState.value.connectedDevices.none { it.deviceAddress == settings.deviceAddress }) return

        // Delega l'applicazione al Service (che ha l'unica istanza di AudioEffectApplier)
        sendToService(AudioEffectService.intentUpdate(
            context = getApplication<Application>(),
            mac = settings.deviceAddress,
            name = settings.deviceName,
            balance = settings.balance,
            boostMb = settings.boostMb,
            enabled = settings.enabled
        ))
    }

    fun deleteDevice(mac: String) {
        viewModelScope.launch {
            repository.delete(mac)
        }
        // Se e' connesso potrebbe essere quello attivo: con lo stesso comando della disconnessione
        // il service lo toglie dall'elenco e riapplica il profilo del precedente, o azzera.
        val device = _uiState.value.connectedDevices.find { it.deviceAddress == mac } ?: return
        sendToService(AudioEffectService.intentReset(getApplication<Application>(), mac, device.deviceName))
    }

    private fun sendToService(intent: Intent) {
        try {
            // Poiché l'utente sta interagendo con l'interfaccia (UI attiva), possiamo usare startService.
            // Se il Service è già attivo in Foreground (perché le cuffie sono connesse), l'intent
            // viene consegnato normalmente a onStartCommand.
            getApplication<Application>().startService(intent)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "startService failed: ${e.message}")
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasBluetoothPermission = granted) }
    }
}
