package com.example.btaudiobalancer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedDevicesTest {

    private val devices = ConnectedDevices()

    @Test
    fun lastConnectedIsActive() {
        devices.connect("A", "Cuffie")
        devices.connect("B", "Auto")
        assertEquals("B", devices.activeMac)
        assertEquals("Auto", devices.activeName)
    }

    @Test
    fun reconnectingMovesDeviceToTheEnd() {
        devices.connect("A", "Cuffie")
        devices.connect("B", "Auto")
        devices.connect("A", "Cuffie")
        assertEquals("A", devices.activeMac)
    }

    @Test
    fun disconnectingActiveFallsBackToPrevious() {
        devices.connect("A", "Cuffie")
        devices.connect("B", "Auto")
        assertTrue(devices.disconnect("B"))
        assertEquals("A", devices.activeMac)
    }

    @Test
    fun disconnectingSecondaryKeepsActive() {
        devices.connect("A", "Cuffie")
        devices.connect("B", "Auto")
        assertFalse(devices.disconnect("A"))
        assertEquals("B", devices.activeMac)
    }

    @Test
    fun disconnectingUnknownDeviceChangesNothing() {
        devices.connect("A", "Cuffie")
        assertFalse(devices.disconnect("X"))
        assertEquals("A", devices.activeMac)
    }

    @Test
    fun joiningEmptyListBecomesActive() {
        devices.join("A", "Cuffie")
        assertEquals("A", devices.activeMac)
    }

    @Test
    fun joiningDoesNotStealActiveButIsNextInLine() {
        devices.connect("A", "Cuffie")
        devices.join("B", "Auto")
        assertEquals("A", devices.activeMac)
        assertTrue(devices.disconnect("A"))
        assertEquals("B", devices.activeMac)
    }

    @Test
    fun joiningKnownDeviceChangesNothing() {
        devices.connect("A", "Cuffie")
        devices.connect("B", "Auto")
        devices.join("B", "Auto")
        assertEquals("B", devices.activeMac)
        assertTrue(devices.disconnect("B"))
        assertEquals("A", devices.activeMac)
    }

    @Test
    fun emptyAfterLastDisconnect() {
        devices.connect("A", "Cuffie")
        assertTrue(devices.disconnect("A"))
        assertTrue(devices.isEmpty)
        assertNull(devices.activeMac)
    }
}
