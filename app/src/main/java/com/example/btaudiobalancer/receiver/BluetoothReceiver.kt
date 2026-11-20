package com.example.btaudiobalancer.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.btaudiobalancer.bluetooth.displayName
import com.example.btaudiobalancer.bluetooth.isAudio
import com.example.btaudiobalancer.service.AudioEffectService

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device ?: return
        // Orologi, mouse ecc. non devono toccare gli effetti
        if (!device.isAudio()) return

        val mac  = device.address ?: return
        val name = device.displayName()

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                startService(context, AudioEffectService.intentApply(context, mac, name))
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Passa il MAC del device disconnesso al Service cosi'
                // puo' resettare solo se corrisponde al device attivo
                startService(context, AudioEffectService.intentReset(context, mac, name))
            }
        }
    }

    private fun startService(context: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // Android 12+: un broadcast Bluetooth non e' tra le eccezioni al divieto di avviare un service
            // in primo piano dal background (ForegroundServiceStartNotAllowedException): passa solo se
            // l'app e' visibile o esclusa dall'ottimizzazione della batteria. Meglio non crollare.
            Log.w("BluetoothReceiver", "service non avviabile dal background: ${e.message}")
        }
    }
}
