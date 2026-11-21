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

private const val TAG = "BluetoothReceiver"

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device ?: return
        val mac  = device.address ?: return
        val name = device.displayName()
        // Orologi, mouse ecc. non devono toccare gli effetti
        val audio = device.isAudio()
        Log.i(TAG, "${intent.action} $name ($mac) audio=$audio")
        if (!audio) return

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
            // Android 12+ (ForegroundServiceStartNotAllowedException): lo stack Bluetooth di AOSP 12 da' ai
            // broadcast ACL un'esenzione temporanea, ma un produttore puo' toglierla. Meglio non crollare.
            Log.w(TAG, "service non avviabile dal background: ${e.message}")
        }
    }
}
