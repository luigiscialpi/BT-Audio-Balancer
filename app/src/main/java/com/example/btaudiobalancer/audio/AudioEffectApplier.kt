package com.example.btaudiobalancer.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.provider.Settings
import android.util.Log
import com.example.btaudiobalancer.data.DeviceAudioSettings

private const val TAG = "AudioEffectApplier"

/**
 * Risultato dell'applicazione del bilanciamento L/R.
 * Indica quale strategia ha avuto successo, o se nessuna ha funzionato.
 */
enum class BalanceResult {
    /** Applicato via Settings.System (affidabile su AOSP/Android 12+) */
    APPLIED_VIA_SETTINGS,
    /** Applicato via AudioManager.setParameters (funziona su MIUI e pochi vendor) */
    APPLIED_VIA_SET_PARAMETERS,
    /** Applicato via DynamicsProcessing (API pubblica Android 9+) */
    APPLIED_VIA_DYNAMICS_PROCESSING,
    /** Nessuna strategia ha funzionato: il device non supporta il balance system-wide */
    NOT_SUPPORTED,
    /** Balance a zero (centro) -- nessuna azione necessaria */
    NEUTRAL
}

/**
 * Applica bilanciamento L/R e boost al sistema audio.
 *
 * Bilanciamento: tripla strategia con fallback:
 *   1. Settings.System.putFloat("master_balance", value) -- AOSP/Android 12+, richiede WRITE_SETTINGS
 *   2. AudioManager.setParameters("master_balance=value") -- MIUI e vendor custom
 *   3. Se entrambi falliscono, ritorna NOT_SUPPORTED per feedback UI
 *
 * Boost: LoudnessEnhancer su audioSessionId=0 (sessione globale) --
 *   funziona su tutti i device Android 9+ senza root.
 *
 * Non sa nulla dei device: e' il service a decidere quale profilo applicare.
 */
class AudioEffectApplier(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: android.media.audiofx.DynamicsProcessing? = null

    /**
     * True se abbiamo applicato qualcosa che reset() deve annullare.
     * shortcut: vive solo in memoria, quindi dopo la morte del processo un master_balance
     * scritto in Settings resta finche' un nuovo apply non lo sovrascrive.
     */
    private var applied = false

    /**
     * Applica le impostazioni di un device.
     *
     * @param settings le impostazioni da applicare
     * @return risultato dell'applicazione del balance
     */
    fun apply(settings: DeviceAudioSettings): BalanceResult {
        if (!settings.enabled) {
            Log.d(TAG, "Device ${settings.deviceName} disabled, resetting")
            reset()
            return BalanceResult.NEUTRAL
        }

        applied = true

        val balanceResult = applyBalance(settings.balance)
        applyBoost(settings.boostMb)
        Log.i(TAG, "Applied: device=${settings.deviceName} " +
                "balance=${settings.balance} boost=${settings.boostMb}mB " +
                "balanceResult=$balanceResult")
        return balanceResult
    }

    /**
     * Ripristina i valori neutri (centro, nessun boost).
     * Chiamare quando il device attivo si disconnette o viene disabilitato.
     * Se non c'e' nulla di applicato non fa niente, per non sovrascrivere il
     * master_balance che l'utente ha impostato dall'Accessibilita'.
     */
    fun reset() {
        if (!applied) return

        applyBalance(0f)
        releaseBoost()
        releaseDynamicsProcessing()
        applied = false
        Log.i(TAG, "Reset to neutral")
    }

    // -- Balance (tripla strategia) -----------------------------------------------

    /**
     * Tenta di applicare il bilanciamento L/R usando due strategie in ordine
     * di affidabilita'. Ritorna il risultato per consentire feedback UI.
     *
     * @param balance da -1.0 (tutto sinistra) a +1.0 (tutto destra), 0 = centro
     * @return quale strategia ha avuto successo
     */
    private fun applyBalance(balance: Float): BalanceResult {
        val clamped = balance.coerceIn(-1f, 1f)

        // Se clamped e' 0f, applichiamo 0f e proviamo comunque il DynamicsProcessing
        // per resettare i guadagni dei canali.
        if (tryDynamicsProcessing(clamped)) {
            Log.d(TAG, "Balance set via DynamicsProcessing: $clamped")
            return if (clamped == 0f) BalanceResult.NEUTRAL else BalanceResult.APPLIED_VIA_DYNAMICS_PROCESSING
        }

        if (clamped == 0f) {
            trySettingsSystem(0f)
            trySetParameters(0f)
            return BalanceResult.NEUTRAL
        }

        // Fallback 1: Settings.System (AOSP/Android 12+, richiede WRITE_SETTINGS)
        if (trySettingsSystem(clamped)) {
            Log.d(TAG, "Balance set via Settings.System: $clamped")
            return BalanceResult.APPLIED_VIA_SETTINGS
        }

        // Fallback 2: AudioManager.setParameters (MIUI e vendor custom)
        if (trySetParameters(clamped)) {
            Log.d(TAG, "Balance set via setParameters: $clamped")
            return BalanceResult.APPLIED_VIA_SET_PARAMETERS
        }

        // Nessuna strategia ha funzionato
        Log.w(TAG, "Balance NOT supported on this device")
        return BalanceResult.NOT_SUPPORTED
    }

    /**
     * Tenta di applicare il bilanciamento L/R usando DynamicsProcessing.
     * Questa API e' disponibile a partire da Android 9 (API 28) ed e' la piu'
     * affidabile perche' applica il guadagno direttamente a livello DSP.
     *
     * @return true se l'effetto e' stato applicato con successo
     */
    private fun tryDynamicsProcessing(balance: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            Log.d(TAG, "tryDynamicsProcessing: Requires API 28+, current is ${android.os.Build.VERSION.SDK_INT}")
            return false
        }
        return try {
            if (dynamicsProcessing == null) {
                // Inizializza l'effetto per 2 canali con tutti gli altri stadi disabilitati
                // (PreEq, MBC, PostEq, Limiter) per minimizzare l'overhead di calcolo
                val builder = android.media.audiofx.DynamicsProcessing.Config.Builder(
                    android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // 2 canali (Stereo)
                    false, 0,
                    false, 0,
                    false, 0,
                    false
                )
                val config = builder.build()
                // audioSession = 0 (sessione globale)
                dynamicsProcessing = android.media.audiofx.DynamicsProcessing(0, 0, config).apply {
                    enabled = true
                }
                Log.d(TAG, "tryDynamicsProcessing: DynamicsProcessing initialized on global session 0")
            }

            val dp = dynamicsProcessing ?: return false
            val leftChannel = dp.getChannelByChannelIndex(0)
            val rightChannel = dp.getChannelByChannelIndex(1)

            // Conversione del bilanciamento [-1.0f, 1.0f] in guadagno decibel (dB)
            // Usiamo una curva logaritmica (attenuazione della potenza) per un effetto naturale
            val leftGainDb: Float
            val rightGainDb: Float

            if (balance < 0f) {
                // Spostamento a sinistra: il canale sinistro rimane a 0 dB, il destro cala
                leftGainDb = 0f
                val rightFactor = (1f + balance).coerceIn(0.0001f, 1f)
                rightGainDb = 20f * kotlin.math.log10(rightFactor)
            } else if (balance > 0f) {
                // Spostamento a destra: il canale destro rimane a 0 dB, il sinistro cala
                val leftFactor = (1f - balance).coerceIn(0.0001f, 1f)
                leftGainDb = 20f * kotlin.math.log10(leftFactor)
                rightGainDb = 0f
            } else {
                leftGainDb = 0f
                rightGainDb = 0f
            }

            leftChannel.setInputGain(leftGainDb)
            rightChannel.setInputGain(rightGainDb)

            dp.setChannelTo(0, leftChannel)
            dp.setChannelTo(1, rightChannel)

            Log.d(TAG, "tryDynamicsProcessing: Applied left=${leftGainDb}dB, right=${rightGainDb}dB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "tryDynamicsProcessing failed: ${e.message}", e)
            false
        }
    }

    private fun releaseDynamicsProcessing() {
        dynamicsProcessing?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Exception) {}
            dynamicsProcessing = null
            Log.d(TAG, "DynamicsProcessing released")
        }
    }

    /**
     * Tenta di scrivere il balance in Settings.System.
     * Richiede il permesso WRITE_SETTINGS concesso dall'utente.
     *
     * @return true se la scrittura e' andata a buon fine
     */
    private fun trySettingsSystem(balance: Float): Boolean {
        return try {
            if (!Settings.System.canWrite(context)) {
                Log.d(TAG, "trySettingsSystem: WRITE_SETTINGS NOT granted")
                return false
            }
            Log.d(TAG, "trySettingsSystem: Attempting to write master_balance = $balance")
            Settings.System.putFloat(
                context.contentResolver,
                "master_balance",
                balance
            )
            val readBack = Settings.System.getFloat(context.contentResolver, "master_balance", -999f)
            Log.d(TAG, "trySettingsSystem: Success. Read back master_balance = $readBack")
            true
        } catch (e: Exception) {
            Log.w(TAG, "trySettingsSystem: Failed to write/verify master_balance: ${e.message}", e)
            false
        }
    }

    /**
     * Tenta il fallback via AudioManager.setParameters.
     * Funziona su MIUI e pochi vendor, ignorato silenziosamente sugli altri.
     *
     * @return true (best-effort: non possiamo verificare se ha realmente funzionato)
     */
    private fun trySetParameters(balance: Float): Boolean {
        return try {
            audioManager.setParameters("master_balance=$balance")
            // Non possiamo verificare se ha effettivamente funzionato,
            // ma non torna false perche' su MIUI funziona
            true
        } catch (e: Exception) {
            Log.w(TAG, "setParameters failed: ${e.message}")
            false
        }
    }

    // -- Boost ---------------------------------------------------------------------

    /**
     * @param boostMb millibel 0..1500 (0 = nessun boost, 1500 = +15 dB)
     */
    private fun applyBoost(boostMb: Int) {
        try {
            releaseBoost()
            if (boostMb <= 0) return

            // audioSessionId = 0 -> sessione globale (tutto l'audio di sistema)
            loudnessEnhancer = LoudnessEnhancer(0).apply {
                setTargetGain(boostMb)
                enabled = true
            }
            Log.d(TAG, "LoudnessEnhancer set to ${boostMb}mB (${boostMb / 100f} dB)")
        } catch (e: Exception) {
            Log.e(TAG, "LoudnessEnhancer failed: ${e.message}")
        }
    }

    private fun releaseBoost() {
        loudnessEnhancer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Exception) {}
            loudnessEnhancer = null
        }
    }

    fun release() {
        releaseBoost()
        releaseDynamicsProcessing()
    }
}
