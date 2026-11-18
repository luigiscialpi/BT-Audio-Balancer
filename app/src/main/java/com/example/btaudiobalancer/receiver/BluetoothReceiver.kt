package com.example.btaudiobalancer.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
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
        val name = try { device.name ?: mac } catch (_: SecurityException) { mac }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val serviceIntent = AudioEffectService.intentApply(context, mac, name)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Passa il MAC del device disconnesso al Service cosi'
                // puo' resettare solo se corrisponde al device attivo
                val serviceIntent = AudioEffectService.intentReset(context, mac, name)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
