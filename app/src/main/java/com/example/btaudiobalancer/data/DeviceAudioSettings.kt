package com.example.btaudiobalancer.data

/**
 * Impostazioni audio per un singolo dispositivo Bluetooth.
 *
 * @param deviceAddress indirizzo MAC del dispositivo (chiave di persistenza)
 * @param deviceName    nome leggibile del dispositivo
 * @param balance       bilanciamento L/R: -1.0 = tutto L, 0.0 = centro, +1.0 = tutto R
 * @param boostMb       amplificazione in millibel (0..1500, pari a 0..+15 dB)
 * @param enabled       se false, non applicare nulla quando il device si connette
 */
data class DeviceAudioSettings(
    val deviceAddress: String,
    val deviceName: String,
    val balance: Float = 0f,
    val boostMb: Int = 0,
    val enabled: Boolean = true
)
