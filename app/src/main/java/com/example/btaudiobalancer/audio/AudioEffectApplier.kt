package com.example.btaudiobalancer.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.example.btaudiobalancer.data.DeviceAudioSettings

private const val TAG = "AudioEffectApplier"

/**
 * Applica bilanciamento L/R e boost al sistema audio.
 *
 * Bilanciamento: tenta setParameters("master_balance=X") — best-effort,
 *   funziona su molti device (MIUI inclusa), ignorato silenziosamente sugli altri.
 *
 * Boost: LoudnessEnhancer su audioSessionId=0 (sessione globale) —
 *   funziona su tutti i device Android 9+ senza root.
 */
class AudioEffectApplier(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null

    /**
     * Applica le impostazioni di un device.
     * Chiamare quando il device si connette.
     */
    fun apply(settings: DeviceAudioSettings) {
        if (!settings.enabled) {
            Log.d(TAG, "Device ${settings.deviceName} disabled, skipping")
            return
        }
        applyBalance(settings.balance)
        applyBoost(settings.boostMb)
        Log.i(TAG, "Applied: device=${settings.deviceName} balance=${settings.balance} boost=${settings.boostMb}mB")
    }

    /**
     * Ripristina i valori neutri (centro, nessun boost).
     * Chiamare quando il device si disconnette.
     */
    fun reset() {
        applyBalance(0f)
        releaseBoost()
        Log.i(TAG, "Reset to neutral")
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    /**
     * @param balance da -1.0 (tutto sinistra) a +1.0 (tutto destra), 0 = centro
     */
    private fun applyBalance(balance: Float) {
        val clamped = balance.coerceIn(-1f, 1f)
        try {
            // API pubblica su Android 9+ — funziona su molti vendor
            audioManager.setParameters("master_balance=$clamped")
            Log.d(TAG, "Balance set via setParameters: $clamped")
        } catch (e: Exception) {
            Log.w(TAG, "setParameters failed: ${e.message}")
        }
    }

    // ── Boost ─────────────────────────────────────────────────────────────────

    /**
     * @param boostMb millibel 0..1500 (0 = nessun boost, 1500 = +15 dB)
     */
    private fun applyBoost(boostMb: Int) {
        try {
            releaseBoost()
            if (boostMb <= 0) return

            // audioSessionId = 0 → sessione globale (tutto l'audio di sistema)
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
    }
}
