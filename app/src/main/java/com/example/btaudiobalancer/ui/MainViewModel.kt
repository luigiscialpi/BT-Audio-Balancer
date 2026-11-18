package com.example.btaudiobalancer.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btaudiobalancer.bluetooth.isAudio
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
                ?.filter { it.isAudio() }
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
