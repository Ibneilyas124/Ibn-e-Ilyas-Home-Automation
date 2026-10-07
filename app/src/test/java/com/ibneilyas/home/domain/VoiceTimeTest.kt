package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTimeTest {
    @Test fun findsMinutesAndHours() {
        assertEquals(1800, VoiceTime.extract("fan 30 minute baad band karo")?.seconds)
        assertEquals(7200, VoiceTime.extract("turn off the fan in 2 hours")?.seconds)
        assertEquals(600, VoiceTime.extract("light off 10 min baad")?.seconds)
        assertNull(VoiceTime.extract("fan band karo"))
    }

    @Test fun detectsForDuration() {
        assertTrue(VoiceTime.extract("light on karo for 15 minutes")!!.forDuration)
        assertFalse(VoiceTime.extract("fan 30 minute baad band karo")!!.forDuration)
    }
}
