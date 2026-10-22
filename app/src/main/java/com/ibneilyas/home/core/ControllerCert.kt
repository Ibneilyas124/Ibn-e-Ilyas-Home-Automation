package com.ibneilyas.home.core

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** A controller is genuine only if its ID carries a certificate signed with the owner's private key. */
object ControllerCert {
    private val idRe = Regex("^ESP32-[0-9A-F]{6}$")

    fun ok(pubB64: String, id: String, cert: String): Boolean {
        if (!idRe.matches(id)) return false
        return try {
            val dec = Base64.getDecoder()
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(dec.decode(pubB64.filter { !it.isWhitespace() })))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(key)
            sig.update(("IBN3|" + id).toByteArray(Charsets.UTF_8))
            sig.verify(dec.decode(cert.filter { !it.isWhitespace() }))
        } catch (e: Exception) {
            false
        }
    }
}
