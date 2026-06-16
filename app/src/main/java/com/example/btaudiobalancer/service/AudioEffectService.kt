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

/**
 * Foreground service che applica gli effetti audio al device Bluetooth connesso.
 *
 * Traccia il MAC del device attivo e resetta solo quando quel device
 * si disconnette (non quando si disconnette un device secondario).
 */
class AudioEffectService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var applier: AudioEffectApplier
    private lateinit var repository: DeviceSettingsRepository

    companion object {
        const val ACTION_APPLY  = "com.example.btaudiobalancer.APPLY"
        const val ACTION_RESET  = "com.example.btaudiobalancer.RESET"
        /** Azione dalla UI per applicare un cambio di impostazioni in tempo reale */
        const val ACTION_UPDATE = "com.example.btaudiobalancer.UPDATE"
        const val EXTRA_MAC     = "device_mac"
        const val EXTRA_NAME    = "device_name"
        const val EXTRA_BALANCE = "device_balance"
        const val EXTRA_BOOST   = "device_boost"
        const val EXTRA_ENABLED = "device_enabled"
        const val CHANNEL_ID    = "bt_audio_balancer"
        private const val NOTIF_ID = 1

        /**
         * Crea un Intent per applicare le impostazioni di un device appena connesso.
         */
        fun intentApply(context: Context, mac: String, name: String) =
            Intent(context, AudioEffectService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_MAC, mac)
                putExtra(EXTRA_NAME, name)
            }

        /**
         * Crea un Intent per resettare gli effetti quando un device si disconnette.
         * Include il MAC del device disconnesso per il reset condizionale.
         */
        fun intentReset(context: Context, mac: String, name: String) =
            Intent(context, AudioEffectService::class.java).apply {
                action = ACTION_RESET
                putExtra(EXTRA_MAC, mac)
                putExtra(EXTRA_NAME, name)
            }

        /**
         * Crea un Intent per aggiornare le impostazioni in tempo reale dalla UI.
         * Usato dal ViewModel per delegare l'applicazione al Service
         * invece di avere un AudioEffectApplier duplicato.
         */
        fun intentUpdate(
            context: Context,
            mac: String,
            name: String,
            balance: Float,
            boostMb: Int,
            enabled: Boolean
        ) = Intent(context, AudioEffectService::class.java).apply {
            action = ACTION_UPDATE
            putExtra(EXTRA_MAC, mac)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_BALANCE, balance)
            putExtra(EXTRA_BOOST, boostMb)
            putExtra(EXTRA_ENABLED, enabled)
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
                        // force=true: alla connessione, questo device diventa attivo
                        applier.apply(settings, force = true)
                        Log.i("AudioEffectService", "Applied settings for $name")
                    } else {
                        // Device nuovo -- nessun profilo salvato, non fare nulla
                        // ma segna comunque come device attivo
                        applier.activeDeviceMac = mac
                        Log.i("AudioEffectService", "New device $name -- no saved profile, skipping")
                    }
                }
            }
            ACTION_RESET -> {
                val mac = intent.getStringExtra(EXTRA_MAC)
                val name = intent.getStringExtra(EXTRA_NAME) ?: mac ?: "unknown"
                // Resetta solo se il device disconnesso e' quello attivo
                applier.reset(mac)

                // Se il device attivo e' stato resettato (activeDeviceMac e' null ora),
                // ferma il service. Altrimenti mantieni il service attivo
                // per il device ancora connesso
                if (applier.activeDeviceMac == null) {
                    Log.i("AudioEffectService", "Active device $name disconnected, stopping service")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    Log.i("AudioEffectService",
                        "Secondary device $name disconnected, keeping service for ${applier.activeDeviceMac}")
                }
            }
            ACTION_UPDATE -> {
                // Aggiornamento in tempo reale dalla UI
                val mac     = intent.getStringExtra(EXTRA_MAC)  ?: return START_NOT_STICKY
                val name    = intent.getStringExtra(EXTRA_NAME) ?: mac
                val balance = intent.getFloatExtra(EXTRA_BALANCE, 0f)
                val boostMb = intent.getIntExtra(EXTRA_BOOST, 0)
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)

                val settings = com.example.btaudiobalancer.data.DeviceAudioSettings(
                    deviceAddress = mac,
                    deviceName = name,
                    balance = balance,
                    boostMb = boostMb,
                    enabled = enabled
                )
                // Se per qualche motivo il Service viene avviato da zero con UPDATE,
                // dobbiamo chiamare startForeground per evitare ForegroundServiceDidNotStartInTimeException
                startForeground(NOTIF_ID, buildNotification("Connesso: $name"))
                
                // Applica solo se e' il device attivo
                applier.apply(settings)
                Log.d("AudioEffectService", "UI update for $name: balance=$balance boost=$boostMb")
            }
        }
        return START_STICKY
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
