package com.example.btaudiobalancer.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Passa ad [action] solo l'ultimo valore ricevuto, al massimo uno ogni [intervalMs].
 * Il primo valore parte subito e l'ultimo arriva sempre, anche se il flusso si ferma.
 */
class LatestThrottle<T>(
    scope: CoroutineScope,
    private val intervalMs: Long,
    private val action: (T) -> Unit
) {
    private val values = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (value in values) {
                action(value)
                delay(intervalMs)
            }
        }
    }

    fun submit(value: T) {
        values.trySend(value)
    }
}
