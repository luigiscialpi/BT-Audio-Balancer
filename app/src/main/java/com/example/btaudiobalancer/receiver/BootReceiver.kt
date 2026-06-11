package com.example.btaudiobalancer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Al boot non facciamo nulla di speciale: il service si avvierà
 * automaticamente quando il prossimo device BT si connetterà.
 * Questo receiver è un placeholder per future espansioni
 * (es. riapplicare settings se c'era già un device connesso prima del reboot).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed — waiting for BT connections")
        }
    }
}
