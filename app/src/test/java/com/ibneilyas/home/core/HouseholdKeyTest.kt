package com.ibneilyas.home.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HouseholdKeyTest {
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3OwiiklDpY0Bis0uTbxpaLXi7k0dSfIpqITYNh7X1pGHYqj6tPluDA4jfQ2a9hx4uyAsw1u/mp4qNNzLVQBUQg=="
    private val key = "K2.SUJOMnxFU1AzMi1BMUIyQzN8NXxLaGFuIEZhbWlseQ==.MEYCIQCsPaLPzB0D8XtSrYMgE4eFbaNJU+CaTppYSVjMLuxp5QIhAMovvx8tw2giEJS1Iif8kOMiMxtgDgR+tSLfFlWqsQcd"

    @Test fun validKeyParses() {
        val h = License.parseHousehold(pub, key)
        assertNotNull(h)
        assertEquals("ESP32-A1B2C3", h!!.espId)
        assertEquals(5, h.max)
        assertEquals("Khan Family", h.name)
    }

    @Test fun editedKeyIsRejected() {
        assertNull(License.parseHousehold(pub, key.replaceFirst("K2.SUJO", "K2.SUJP")))
        assertNull(License.parseHousehold(pub, key.replaceFirst("K2.", "K2.A")))
    }

    @Test fun whitespaceInPastedKeyIsIgnored() {
        assertNotNull(License.parseHousehold(pub, key.substring(0, 50) + "\n" + key.substring(50)))
    }

    @Test fun otherThingsAreNotHouseholdKeys() {
        assertNull(License.parseHousehold(pub, "MEUCIQCcXgfhnPfRqoPXmzPsPF0/bqugt4+PBPFlzNRdnioBLwIgKgdl4F8YbOeanR6QyiyL789nH1ku3WGyuSmRZXvReqM="))
        assertNull(License.parseHousehold(pub, ""))
        assertNull(License.parseHousehold("bad key", key))
    }
}
