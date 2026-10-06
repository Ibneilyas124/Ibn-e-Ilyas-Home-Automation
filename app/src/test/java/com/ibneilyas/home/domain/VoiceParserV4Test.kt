package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserV4Test {
    private fun app(id: String, name: String, t: ApplianceType, room: String, ch: Int) =
        Appliance(id, name, t, room, "n", ch)

    private fun data() = HomeData(
        rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Sheraz's Room", "bed")
        ),
        appliances = listOf(
            app("a1", "Living Light", ApplianceType.LIGHT, "r1", 1),
            app("a2", "Ceiling Fan", ApplianceType.FAN, "r1", 2),
            app("a3", "Living Light", ApplianceType.LIGHT, "r2", 1),
            app("a4", "Bed Light", ApplianceType.LIGHT, "r2", 2),
            app("a5", "Ceiling Fan", ApplianceType.FAN, "r2", 3)
        )
    )

    private fun dev(t: String, id: String, on: Boolean, active: String? = "r1") {
        val r = VoiceParser.parse(t, data(), emptyList(), active)
        assertTrue("$t -> $r", r is VoiceResult.Device)
        r as VoiceResult.Device
        assertEquals("$t -> $r", id, r.a.id)
        assertEquals("$t -> $r", on, r.on)
    }

    @Test fun ignoresChatter() {
        dev("mehrbani karke sheraz ke kamre ki living light on karo pyare", "a3", true)
        dev("pyare fan band karo bhai", "a2", false)
        dev("sheraz ka fan off kardo meri jaan", "a5", false)
        dev("please zara living light on kar do pyare", "a1", true)
        dev("oye sarfraz ke kamre ka fan on kardo yaar", "a2", true, active = "r2")
    }

    @Test fun stillAsksWhenUnsure() {
        val r = VoiceParser.parse("pyare sheraz ke kamre ki light off karo", data(), emptyList(), "r1")
        assertTrue("$r", r is VoiceResult.Message && r.text.startsWith("Which light"))
    }

    @Test fun doesNotGuessFromChatterOnly() {
        val a = VoiceParser.parse("kuch bhi on karo pyare", data(), emptyList(), "r1")
        assertTrue("$a", a is VoiceResult.Message && a.text.contains("could not find"))
        val b = VoiceParser.parse("mehrbani karke pyare", data(), emptyList(), "r1")
        assertTrue("$b", b is VoiceResult.Message)
        val c = VoiceParser.parse("zero bulb on", data(), emptyList(), "r1")
        assertTrue("$c", c is VoiceResult.Message && c.text.contains("Not sure about: zero"))
    }
}
