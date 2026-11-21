package com.example.btaudiobalancer.bluetooth

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build

/**
 * False solo se il sistema dice che NON e' un device audio (orologio, mouse, telefono...).
 * Classe sconosciuta conta come audio: il sistema non la riporta come null ma come MISC (0) o
 * UNCATEGORIZED finche' non l'ha letta, per esempio subito dopo l'accensione del Bluetooth o se
 * BLUETOOTH_CONNECT e' negato; scartare questi casi faceva perdere l'evento di connessione.
 * shortcut: i device solo-LE non hanno classe e passano il filtro; se disturbano gli effetti, escludere DEVICE_TYPE_LE.
 */
fun BluetoothDevice.isAudio(): Boolean =
    isAudioClass(try { bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null })

internal fun isAudioClass(major: Int?): Boolean = when (major) {
    null, BluetoothClass.Device.Major.MISC, BluetoothClass.Device.Major.UNCATEGORIZED,
    BluetoothClass.Device.Major.AUDIO_VIDEO -> true
    else -> false
}

/** Nome da mostrare; preferisce l'alias utente (API 30+) al nome di fabbrica, altrimenti MAC. */
fun BluetoothDevice.displayName(): String = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        alias ?: name ?: address
    } else {
        name ?: address
    }
} catch (_: SecurityException) {
    address
}

/**
 * Chiede al sistema i device A2DP connessi adesso e li passa a [onResult]: sul thread principale,
 * o subito se la richiesta non parte. null = non si puo' sapere (Bluetooth spento, permesso negato).
 * Sostituisce BluetoothDevice.isConnected(), che e' un'API nascosta.
 * shortcut: solo A2DP, niente LE Audio (altro profilo); da estendere se serve.
 */
fun Context.queryConnectedA2dp(onResult: (List<BluetoothDevice>?) -> Unit) {
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val devices = try { proxy.connectedDevices } catch (_: SecurityException) { null }
            adapter?.closeProfileProxy(profile, proxy)
            onResult(devices)
        }

        override fun onServiceDisconnected(profile: Int) {}
    }
    val asked = try {
        adapter != null && adapter.isEnabled && adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP)
    } catch (_: SecurityException) {
        false
    }
    if (!asked) onResult(null)
}
