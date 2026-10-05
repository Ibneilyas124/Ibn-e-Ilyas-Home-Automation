package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserV3Test {
    private fun app(id: String, name: String, t: ApplianceType, room: String, ch: Int) =
        Appliance(id, name, t, room, "n", ch)

    private fun data() = HomeData(
        rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Sheraz's Room", "bed"),
            Room("r3", "Drawing Room", "sofa")
        ),
        appliances = listOf(
            app("a1", "Zero Bulb", ApplianceType.LIGHT, "r1", 1),
            app("a2", "Ceiling Fan", ApplianceType.FAN, "r1", 2),
            app("a3", "Tube Light", ApplianceType.LIGHT, "r1", 3),
            app("a4", "Room Light", ApplianceType.LIGHT, "r2", 1),
            app("a5", "Bed Light", ApplianceType.LIGHT, "r3", 1),
            app("a6", "Main Light", ApplianceType.LIGHT, "r3", 2)
        )
    )

    private fun dev(t: String, id: String, on: Boolean, active: String? = "r1") {
        val r = VoiceParser.parse(t, data(), emptyList(), active)
        assertTrue("$t -> $r", r is VoiceResult.Device)
        r as VoiceResult.Device
        assertEquals("$t -> $r", id, r.a.id)
        assertEquals("$t -> $r", on, r.on)
    }

    @Test fun bulbMeansBulb() {
        dev("bulb off karo", "a1", false)
        dev("bulb on karo", "a1", true)
        dev("zero bulb on", "a1", true)
        dev("light off karo", "a3", false)
    }

    @Test fun misheardWords() {
        dev("bulb of karo", "a1", false)
        dev("bulb carro on", "a1", true)
        dev("bulb band kar do", "a1", false)
        dev("fan ban karo", "a2", false)
    }

    @Test fun bothOnAndOffAsks() {
        val r = VoiceParser.parse("bulb off on karo", data(), emptyList(), "r1")
        assertTrue("$r", r is VoiceResult.Message && r.text.contains("either"))
    }

    @Test fun alternativesAreUsed() {
        val (r, heard) = VoiceParser.parseBest(listOf("bulb up karo", "bulb off karo"), data(), emptyList(), "r1")
        assertTrue("$r", r is VoiceResult.Device && r.a.id == "a1" && !r.on)
        assertEquals("bulb off karo", heard)
        val (s, _) = VoiceParser.parseBest(listOf("bulb up", "bulb off karo"), data(), emptyList(), "r1")
        assertTrue("$s", s is VoiceResult.Message)
        val (t, _) = VoiceParser.parseBest(listOf("light on karo", "bed light on karo"), data(), emptyList(), "r3")
        assertTrue("$t", t is VoiceResult.Message && t.text.startsWith("Which light"))
    }
}
