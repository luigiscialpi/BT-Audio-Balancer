package com.example.btaudiobalancer.bluetooth

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * False solo se il sistema dice che NON e' un device audio (orologio, mouse, telefono...).
 * Classe sconosciuta (null, o BLUETOOTH_CONNECT negato) conta come audio.
 * shortcut: i device solo-LE non hanno classe e passano il filtro; se disturbano gli effetti, escludere DEVICE_TYPE_LE.
 */
fun BluetoothDevice.isAudio(): Boolean {
    val major = try { bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null }
    return major == null || major == BluetoothClass.Device.Major.AUDIO_VIDEO
}

/** Nome da mostrare; se non si puo' leggere (BLUETOOTH_CONNECT negato) vale il MAC. */
fun BluetoothDevice.displayName(): String = try { name ?: address } catch (_: SecurityException) { address }

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
