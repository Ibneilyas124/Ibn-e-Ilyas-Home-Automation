package com.ibneilyas.home.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SchedTest {
    @Test fun parseAndEncodeRoundTrip() {
        val l = SchedCodec.parse("n1", "18,0,127,1,1;23,30,62,2,0;")
        assertEquals(2, l.size)
        assertEquals(SchedEntry("n1", 18, 0, 127, 1, true), l[0])
        assertEquals(SchedEntry("n1", 23, 30, 62, 2, false), l[1])
        assertEquals("18,0,127,1,1;23,30,62,2,0", SchedCodec.encode(l))
    }

    @Test fun badPartsAreSkipped() {
        assertEquals(1, SchedCodec.parse("n", "x;1,2,3;7,5,127,1,0;;").size)
        assertEquals(0, SchedCodec.parse("n", "").size)
    }

    @Test fun timeText() {
        assertEquals("6:00 PM", SchedFormat.time(18, 0))
        assertEquals("12:05 AM", SchedFormat.time(0, 5))
        assertEquals("12:30 PM", SchedFormat.time(12, 30))
        assertEquals("11:59 PM", SchedFormat.time(23, 59))
    }

    @Test fun daysText() {
        assertEquals("Every day", SchedFormat.days(127))
        assertEquals("Mon-Fri", SchedFormat.days(62))
        assertEquals("Sat, Sun", SchedFormat.days(65))
        assertEquals("Mon", SchedFormat.days(2))
        assertEquals("Tue, Sun", SchedFormat.days(5))
        assertEquals("No days", SchedFormat.days(0))
    }
}
