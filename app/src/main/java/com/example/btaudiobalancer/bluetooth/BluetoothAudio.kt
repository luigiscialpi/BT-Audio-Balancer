package com.example.btaudiobalancer.bluetooth

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice

/**
 * False solo se il sistema dice che NON e' un device audio (orologio, mouse, telefono...).
 * Classe sconosciuta (null, o BLUETOOTH_CONNECT negato) conta come audio.
 * shortcut: i device solo-LE non hanno classe e passano il filtro; se disturbano gli effetti, escludere DEVICE_TYPE_LE.
 */
fun BluetoothDevice.isAudio(): Boolean {
    val major = try { bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null }
    return major == null || major == BluetoothClass.Device.Major.AUDIO_VIDEO
}
