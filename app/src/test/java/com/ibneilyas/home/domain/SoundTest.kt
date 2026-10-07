package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundTest {
    @Test fun similarSpellingsShareASound() {
        assertEquals(Sound.key("kitchen"), Sound.key("kichen"))
        assertEquals(Sound.key("kitchen"), Sound.key("kitchin"))
        assertEquals(Sound.key("sarfraz"), Sound.key("sarfaraz"))
        assertEquals(Sound.key("sheraz"), Sound.key("sheras"))
    }

    @Test fun matchIsSafe() {
        val known = setOf("kitchen", "living", "sarfraz")
        assertEquals("kitchen", Sound.match("kichen", known))
        assertNull(Sound.match("cabin", known))
        assertNull(Sound.match("fan", known))
    }
}
