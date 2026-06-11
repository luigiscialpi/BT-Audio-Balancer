package com.example.btaudiobalancer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.btaudiobalancer.MainActivity
import com.example.btaudiobalancer.audio.AudioEffectApplier
import com.example.btaudiobalancer.data.DeviceSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AudioEffectService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var applier: AudioEffectApplier
    private lateinit var repository: DeviceSettingsRepository

    companion object {
        const val ACTION_APPLY  = "com.example.btaudiobalancer.APPLY"
        const val ACTION_RESET  = "com.example.btaudiobalancer.RESET"
        const val EXTRA_MAC     = "device_mac"
        const val EXTRA_NAME    = "device_name"
        const val CHANNEL_ID    = "bt_audio_balancer"
        private const val NOTIF_ID = 1

        fun intentApply(context: Context, mac: String, name: String) =
            Intent(context, AudioEffectService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_MAC, mac)
                putExtra(EXTRA_NAME, name)
            }

        fun intentReset(context: Context) =
            Intent(context, AudioEffectService::class.java).apply {
                action = ACTION_RESET
            }
    }

    override fun onCreate() {
        super.onCreate()
        applier    = AudioEffectApplier(this)
        repository = DeviceSettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APPLY -> {
                val mac  = intent.getStringExtra(EXTRA_MAC)  ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: mac
                startForeground(NOTIF_ID, buildNotification("Connesso: $name"))
                scope.launch {
                    val settings = repository.deviceFlow(mac).firstOrNull()
                    if (settings != null) {
                        applier.apply(settings)
                        Log.i("AudioEffectService", "Applied settings for $name")
                    } else {
                        // Device nuovo — nessun profilo salvato, non fare nulla
                        Log.i("AudioEffectService", "New device $name — no saved profile, skipping")
                    }
                }
            }
            ACTION_RESET -> {
                applier.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        applier.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BT Audio Balancer",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Effetti audio Bluetooth attivi" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("BT Audio Balancer")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
