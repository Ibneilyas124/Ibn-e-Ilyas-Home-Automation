package com.ibneilyas.home.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerCertTest {
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEyPvbf2k3/8eynTI5TOGE9SrgzEl0CFxGLJXONrdN58CnXCPs+8mXfJSEW96k2ujBx8nuXd2/ma9ZVe6kv8uNmg=="
    private val id = "ESP32-A1B2C3"
    private val cert = "MEUCIQCVPM9j18nWfhyPzDjZ9v09W3DdSGVd7f0t+xgRnGi4+gIgY+dtfrDxjSM7QGSikNBthQgWyBNyGKmFtClUKSvFvfk="

    @Test fun signedControllerIsGenuine() {
        assertTrue(ControllerCert.ok(pub, id, cert))
    }

    @Test fun certificateBelongsToOneIdOnly() {
        assertFalse(ControllerCert.ok(pub, "ESP32-A1B2C4", cert))
    }

    @Test fun missingOrBrokenCertificateIsRejected() {
        assertFalse(ControllerCert.ok(pub, id, ""))
        assertFalse(ControllerCert.ok(pub, id, "not a certificate"))
        assertFalse(ControllerCert.ok(pub, id, cert.substring(0, 40)))
    }

    @Test fun badIdOrKeyIsRejected() {
        assertFalse(ControllerCert.ok(pub, "esp32-a1b2c3", cert))
        assertFalse(ControllerCert.ok("bad key", id, cert))
    }
}
