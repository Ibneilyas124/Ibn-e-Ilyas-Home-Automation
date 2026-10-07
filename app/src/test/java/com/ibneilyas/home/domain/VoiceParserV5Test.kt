package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserV5Test {
    private fun app(id: String, name: String, t: ApplianceType, room: String, ch: Int) =
        Appliance(id, name, t, room, "n", ch)

    private fun data() = HomeData(
        rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Kitchen", "kitchen")
        ),
        appliances = listOf(
            app("a1", "Living Light", ApplianceType.LIGHT, "r1", 1),
            app("a2", "Ceiling Fan", ApplianceType.FAN, "r1", 2),
            app("a3", "Kitchen Light", ApplianceType.LIGHT, "r2", 1),
            app("a4", "Exhaust Fan", ApplianceType.FAN, "r2", 2)
        )
    )

    private fun dev(t: String, id: String, on: Boolean, active: String? = "r1") {
        val r = VoiceParser.parse(t, data(), emptyList(), active)
        assertTrue("$t -> $r", r is VoiceResult.Device)
        r as VoiceResult.Device
        assertEquals("$t -> $r", id, r.a.id)
        assertEquals("$t -> $r", on, r.on)
    }

    @Test fun commonMishearingsOfKitchen() {
        dev("listen light on karo", "a3", true)
        dev("itching light on karo", "a3", true)
        dev("kitchin fan off karo", "a4", false)
        dev("chicken light off", "a3", false)
    }

    @Test fun alternativesPreferTheNamedRoom() {
        val alts = listOf("zzz light on karo", "kitchen light on karo")
        val (r, heard) = VoiceParser.parseBest(alts, data(), emptyList(), "r1")
        assertTrue("$r", r is VoiceResult.Device && r.a.id == "a3" && r.on)
        assertEquals("kitchen light on karo", heard)
    }

    @Test fun userTaughtWordsWork() {
        Lexicon.setExtra(mapOf("pankho" to "fan", "balo" to "on"))
        try {
            dev("pankho balo karo", "a2", true)
        } finally {
            Lexicon.setExtra(emptyMap())
        }
    }
}
