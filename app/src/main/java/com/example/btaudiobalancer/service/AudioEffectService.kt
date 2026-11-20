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
import com.example.btaudiobalancer.bluetooth.displayName
import com.example.btaudiobalancer.bluetooth.queryConnectedA2dp
import com.example.btaudiobalancer.data.DeviceAudioSettings
import com.example.btaudiobalancer.data.DeviceSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Foreground service che applica gli effetti audio al device Bluetooth connesso.
 *
 * Tiene l'elenco dei device connessi che hanno un profilo salvato. Gli effetti sono
 * globali, quindi vale il profilo dell'ultimo connesso; quando si disconnette torna
 * quello del device precedente, e se non ne resta nessuno azzera e si ferma.
 * I comandi passano da una coda e vengono eseguiti uno alla volta.
 * Se il sistema riavvia il service dopo averne ucciso il processo (intent nullo), i device
 * connessi si rileggono dal sistema (restoreConnected).
 */
class AudioEffectService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var applier: AudioEffectApplier
    private lateinit var repository: DeviceSettingsRepository

    /** Comandi (intent e startId) in ordine di arrivo: l'applier non e' thread-safe. */
    private val commands = Channel<Pair<Intent, Int>>(Channel.UNLIMITED)

    private val connected = ConnectedDevices()

    /** Testo della notifica in primo piano; null finche' il service non lo e'. */
    private var notificationText: String? = null

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
        private const val TAG = "AudioEffectService"

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
        scope.launch {
            for ((intent, startId) in commands) {
                try {
                    handle(intent, startId)
                } catch (e: Exception) {
                    // Un comando fallito non deve bloccare quelli che seguono
                    Log.e(TAG, "Command ${intent.action} failed", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Il sistema vuole startForeground subito dopo startForegroundService, qualunque sia
        // l'azione (anche al restart senza intent); il testo con il nome del device lo mette handle().
        // A service gia' in primo piano non lo richiede, e la notifica non si riposta se non cambia.
        try {
            showNotification(notificationText ?: "Effetti audio attivi")
        } catch (e: RuntimeException) {
            if (e !is SecurityException && e !is IllegalStateException) throw e
            // SecurityException (Android 14+: il tipo connectedDevice richiede BLUETOOTH_CONNECT, es.
            // revocato mentre il service era attivo) o IllegalStateException (Android 12+: avvio in
            // primo piano non consentito dal background). Senza foreground il service non serve: ci si ferma.
            // shortcut: da startForegroundService il sistema darebbe comunque l'errore dei 5 secondi.
            Log.w(TAG, "startForeground refused: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent == null) restoreConnected(startId) else commands.trySend(intent to startId)
        return START_STICKY
    }

    /**
     * Restart del sistema senza intent: gli effetti sono andati persi con il processo.
     * Si rileggono i device A2DP gia' connessi e si accodano come nuove connessioni; quelli
     * senza profilo vengono ignorati e, se non resta nulla, il service si ferma da solo.
     * shortcut: l'ordine di connessione originale non si conosce, vale quello restituito
     * dal sistema; da raffinare se serve.
     */
    private fun restoreConnected(startId: Int) {
        queryConnectedA2dp { devices ->
            if (devices.isNullOrEmpty()) {
                stopSelf(startId) // niente da ripristinare, o non si sa chi e' connesso
            } else {
                for (d in devices) commands.trySend(intentApply(this, d.address, d.displayName()) to startId)
            }
        }
    }

    private suspend fun handle(intent: Intent, startId: Int) {
        val mac  = intent.getStringExtra(EXTRA_MAC) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: mac
        when (intent.action) {
            ACTION_APPLY -> {
                val settings = repository.getOnce(mac)
                if (settings == null) {
                    // Senza profilo non si toccano gli effetti: potrebbero essere di un altro device
                    Log.i(TAG, "$name: no saved profile, ignoring")
                } else {
                    connected.connect(mac, name)
                    applier.apply(settings)
                }
            }
            ACTION_RESET -> {
                if (connected.disconnect(mac)) applyActive()
            }
            ACTION_UPDATE -> {
                // La UI considera connesso questo device: se il service non lo conosceva (appena
                // avviato, o profilo appena creato) ci si fida, senza scavalcare l'attivo
                connected.join(mac, name)
                // Un device secondario si salva (lo fa il ViewModel) ma non si applica
                if (connected.activeMac == mac) {
                    applier.apply(DeviceAudioSettings(
                        deviceAddress = mac,
                        deviceName    = name,
                        balance       = intent.getFloatExtra(EXTRA_BALANCE, 0f),
                        boostMb       = intent.getIntExtra(EXTRA_BOOST, 0),
                        enabled       = intent.getBooleanExtra(EXTRA_ENABLED, true)
                    ))
                }
            }
        }
        if (connected.isEmpty) {
            applier.reset()
            stopSelf(startId) // ignorato se nel frattempo e' arrivato un comando piu' recente
        } else {
            showNotification("Connesso: ${connected.activeName}")
        }
    }

    /** Applica il profilo dell'ultimo device connesso; azzera se non ce n'e' o non ha piu' un profilo. */
    private suspend fun applyActive() {
        val settings = connected.activeMac?.let { repository.getOnce(it) }
        if (settings == null) applier.reset() else applier.apply(settings)
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

    /** Ripubblica la notifica solo se il testo cambia: l'anteprima degli slider manda fino a 10 comandi al secondo. */
    private fun showNotification(text: String) {
        if (text == notificationText) return
        startForeground(NOTIF_ID, buildNotification(text))
        notificationText = text
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
