package com.ibneilyas.home.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserV6Test {
    private fun app(id: String, name: String, t: ApplianceType, room: String, ch: Int) =
        Appliance(id, name, t, room, "n", ch)

    private fun data() = HomeData(
        rooms = listOf(Room("r1", "Sarfraz's Room", "bed")),
        appliances = listOf(
            app("a1", "Living Light", ApplianceType.LIGHT, "r1", 1),
            app("a2", "Ceiling Fan", ApplianceType.FAN, "r1", 2)
        )
    )

    private fun p(t: String) = VoiceParser.parse(t, data(), emptyList(), "r1")

    @Test fun timerAfter() {
        val r = p("fan 30 minute baad band karo")
        assertTrue("$r", r is VoiceResult.Device && r.a.id == "a2" && !r.on && r.delaySec == 1800 && !r.forDuration)
    }

    @Test fun timerFor() {
        val r = p("living light on karo for 15 minutes")
        assertTrue("$r", r is VoiceResult.Device && r.a.id == "a1" && r.on && r.delaySec == 900 && r.forDuration)
    }

    @Test fun groupTimerRefused() {
        val r = p("all lights off 10 minute baad")
        assertTrue("$r", r is VoiceResult.Message && r.text.startsWith("Timers work"))
    }

    @Test fun plainCommandUnchanged() {
        val r = p("fan band karo")
        assertTrue("$r", r is VoiceResult.Device && r.a.id == "a2" && !r.on && r.delaySec == 0)
    }
}
