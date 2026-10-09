package com.ibneilyas.home.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTest {
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEY0AjVy2DJN3FTiOTiLtCjNsoK3PU/rnZp7dAtiFPjW4OAEmLCndrUJHRzxRUmO9XDUmslVEbnK14iUhkl7ZYWQ=="
    private val id = "A1B2-C3D4-E5F6"
    private val code = "MEUCIQCcXgfhnPfRqoPXmzPsPF0/bqugt4+PBPFlzNRdnioBLwIgKgdl4F8YbOeanR6QyiyL789nH1ku3WGyuSmRZXvReqM="

    @Test fun validCodeWorks() {
        assertTrue(License.verify(pub, id, code))
    }

    @Test fun codeWorksOnlyOnItsOwnPhone() {
        assertFalse(License.verify(pub, "A1B2-C3D4-E5F7", code))
    }

    @Test fun pastedCodeWithLineBreaksStillWorks() {
        assertTrue(License.verify(pub, id, code.substring(0, 40) + "\n" + code.substring(40)))
    }

    @Test fun garbageIsRejected() {
        assertFalse(License.verify(pub, id, "not a code"))
        assertFalse(License.verify(pub, id, ""))
        assertFalse(License.verify("bad key", id, code))
    }
}
