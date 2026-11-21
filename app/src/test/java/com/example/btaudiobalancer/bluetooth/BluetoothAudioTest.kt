package com.example.btaudiobalancer.bluetooth

import android.bluetooth.BluetoothClass.Device.Major
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAudioTest {

    @Test
    fun audioDeviceIsAudio() = assertTrue(isAudioClass(Major.AUDIO_VIDEO))

    @Test
    fun unknownClassIsAudio() {
        assertTrue("null", isAudioClass(null))
        assertTrue("MISC: classe non ancora letta", isAudioClass(Major.MISC))
        assertTrue("UNCATEGORIZED", isAudioClass(Major.UNCATEGORIZED))
    }

    @Test
    fun otherKnownClassesAreNotAudio() {
        for (major in listOf(Major.COMPUTER, Major.PHONE, Major.PERIPHERAL, Major.WEARABLE, Major.HEALTH)) {
            assertFalse("0x${major.toString(16)}", isAudioClass(major))
        }
    }
}
