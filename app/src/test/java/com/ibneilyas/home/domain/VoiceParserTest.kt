package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private fun app(id: String, name: String, t: ApplianceType, room: String, ch: Int) =
        Appliance(id, name, t, room, "n", ch)

    private fun base(extra: List<Appliance> = emptyList()): HomeData {
        val rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Sheraz's Room", "bed"),
            Room("r3", "Drawing Room", "sofa"),
            Room("r4", "Kitchen", "kitchen")
        )
        val apps = listOf(
            app("a1", "Living Light", ApplianceType.LIGHT, "r1", 1),
            app("a2", "Ceiling Fan", ApplianceType.FAN, "r1", 2),
            app("a3", "Bed Light", ApplianceType.LIGHT, "r1", 3),
            app("a4", "Power Socket", ApplianceType.SOCKET, "r1", 4),
            app("a5", "Room Light", ApplianceType.LIGHT, "r2", 1),
            app("a6", "Ceiling Fan", ApplianceType.FAN, "r2", 2),
            app("a7", "Power Socket", ApplianceType.SOCKET, "r2", 3),
            app("a8", "Main Light", ApplianceType.LIGHT, "r3", 1),
            app("a9", "Ceiling Fan", ApplianceType.FAN, "r3", 2),
            app("a10", "Kitchen Light", ApplianceType.LIGHT, "r4", 1),
            app("a11", "Exhaust Fan", ApplianceType.FAN, "r4", 2)
        )
        return HomeData(rooms = rooms, appliances = apps + extra)
    }

    private fun go(t: String, active: String? = "r1", d: HomeData = base()): VoiceResult =
        VoiceParser.parse(t, d, emptyList(), active)

    private fun dev(t: String, id: String, on: Boolean, active: String? = "r1", d: HomeData = base()) {
        val r = go(t, active, d)
        assertTrue("$t -> $r", r is VoiceResult.Device)
        r as VoiceResult.Device
        assertEquals("$t -> $r", id, r.a.id)
        assertEquals("$t -> $r", on, r.on)
    }

    private fun msg(t: String, part: String, active: String? = "r1", d: HomeData = base()) {
        val r = go(t, active, d)
        assertTrue("$t -> $r", r is VoiceResult.Message && r.text.contains(part))
    }

    @Test fun existingCommands() {
        dev("fan band karo", "a2", false)
        dev("پنکھا بند کرو", "a2", false)
        dev("fan off karo", "a2", false)
        dev("fan on karo", "a2", true)
        dev("fan off kar do", "a2", false)
        dev("fan on kar do", "a2", true)
    }

    @Test fun romanUrduVariants() {
        dev("panka band karo", "a2", false)
        dev("panka off kardo", "a2", false)
        dev("fan chalao", "a2", true)
        dev("fan chala do", "a2", true)
        dev("fan chalu karo", "a2", true)
        dev("off karo fan", "a2", false)
        dev("fan ko off karo", "a2", false)
        dev("پنکھا چلا دو", "a2", true)
        dev("please zara fan band kar do", "a2", false)
        dev("fann off karo", "a2", false)
    }

    @Test fun roomSpecific() {
        dev("Sarfraz ke kamre ka fan band karo", "a2", false, active = "r3")
        dev("Sheraz ke room ka fan off karo", "a6", false)
        dev("Sheraz ke room ki light on karo", "a5", true)
        dev("Drawing room ki light on karo", "a8", true)
        dev("turn on kitchen light", "a10", true)
        dev("kitchn light on", "a10", true)
    }

    @Test fun activeRoomSwitch() {
        dev("fan off karo", "a2", false, active = "r1")
        dev("fan off karo", "a6", false, active = "r2")
        dev("fan off karo", "a9", false, active = "r3")
        dev("pankha band karo", "a2", false, active = "r1")
        dev("lite on karo", "a5", true, active = "r2")
    }

    @Test fun ambiguityAsksInsteadOfGuessing() {
        msg("light on karo", "Which light")
        val d = base(listOf(app("a12", "Bed Fan", ApplianceType.FAN, "r1", 5)))
        msg("fan band karo", "Which fan", d = d)
        dev("bed wala fan band karo", "a12", false, d = d)
        msg("fan band karo", "Which fan", active = null)
    }

    @Test fun noGuessing() {
        msg("zero bulb on", "Not sure about: zero")
        msg("fun off karo", "could not find")
        msg("socket on karo", "No socket in Kitchen", active = "r4")
    }

    @Test fun roomSelectAndGroups() {
        val s = go("Sheraz ke room mein jao")
        assertTrue("$s", s is VoiceResult.SelectRoom && s.room.id == "r2")
        val t = go("Drawing room select karo")
        assertTrue("$t", t is VoiceResult.SelectRoom && t.room.id == "r3")
        val g = go("all lights off")
        assertTrue("$g", g is VoiceResult.Group && g.list.size == 5 && !g.on)
        val u = go("سب لائٹس بند کرو")
        assertTrue("$u", u is VoiceResult.Group && u.list.size == 5)
    }
}
