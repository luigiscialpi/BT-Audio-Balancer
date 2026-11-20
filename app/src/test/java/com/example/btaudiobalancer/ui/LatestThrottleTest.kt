package com.example.btaudiobalancer.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LatestThrottleTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val received = CopyOnWriteArrayList<Int>()
    private val throttle = LatestThrottle<Int>(scope, intervalMs = 100) { received.add(it) }

    @After
    fun tearDown() = scope.cancel()

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < end) Thread.sleep(5)
    }

    @Test
    fun firstValueIsDelivered() {
        throttle.submit(1)
        waitUntil { received.size == 1 }
        assertEquals(listOf(1), received.toList())
    }

    @Test
    fun burstKeepsOnlyTheLastValue() {
        throttle.submit(1)
        waitUntil { received.size == 1 }
        for (i in 2..50) throttle.submit(i)
        waitUntil { received.size == 2 }
        Thread.sleep(300) // niente altro deve arrivare
        assertEquals(listOf(1, 50), received.toList())
    }

    @Test
    fun valuesSpacedMoreThanTheIntervalAreAllDelivered() {
        for (i in 1..3) {
            throttle.submit(i)
            waitUntil { received.size == i }
            Thread.sleep(250)
        }
        assertEquals(listOf(1, 2, 3), received.toList())
    }

    @Test
    fun continuousStreamIsThrottledAndEndsOnTheLastValue() {
        var last = 0
        val end = System.nanoTime() + 450_000_000L
        while (System.nanoTime() < end) {
            throttle.submit(++last)
            Thread.sleep(5)
        }
        waitUntil { received.lastOrNull() == last }
        assertEquals(last, received.last())
        // 450 ms a un valore ogni 100 ms: al massimo 5 consegne piu' l'ultima, con un po' di margine
        assertTrue("consegne: ${received.size}", received.size in 2..7)
    }
}
