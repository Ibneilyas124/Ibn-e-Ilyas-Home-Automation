package com.ibneilyas.home.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRetryTest {
    @Test fun retryOnlyWhenNothingUnderstood() {
        assertTrue(VoiceParser.needsRetry(VoiceResult.Message("Say ON or OFF, for example: fan off karo")))
        assertTrue(VoiceParser.needsRetry(VoiceResult.Message("I could not find that device. Heard: x")))
        assertFalse(VoiceParser.needsRetry(VoiceResult.Message("Which fan? Ceiling Fan or Bed Fan.")))
        assertFalse(VoiceParser.needsRetry(VoiceResult.Message("Please say either ON or OFF, not both")))
    }
}
