package com.ibneilyas.home.core

import android.content.Context
import android.provider.Settings
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Per-phone activation: the code is a signature over this phone's Device ID, made with the owner's private key. */
object License {
    private const val PREFS = "lic"

    fun verify(pubB64: String, deviceId: String, code: String): Boolean = try {
        val dec = Base64.getDecoder()
        val key = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(dec.decode(pubB64.filter { !it.isWhitespace() })))
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(key)
        sig.update(("IBN1|" + deviceId).toByteArray(Charsets.UTF_8))
        sig.verify(dec.decode(code.filter { !it.isWhitespace() }))
    } catch (e: Exception) {
        false
    }

    fun deviceId(ctx: Context): String {
        val aid = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val raw = ("ibnhome|" + aid + "|" + ctx.packageName).toByteArray(Charsets.UTF_8)
        val h = MessageDigest.getInstance("SHA-256").digest(raw)
        return h.joinToString("") { "%02X".format(it) }.take(12).chunked(4).joinToString("-")
    }

    fun isActive(ctx: Context): Boolean {
        val code = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("code", "") ?: ""
        return code.isNotEmpty() && verify(LicenseKey.PUBLIC_B64, deviceId(ctx), code)
    }

    fun activate(ctx: Context, code: String): Boolean {
        val c = code.filter { !it.isWhitespace() }
        if (!verify(LicenseKey.PUBLIC_B64, deviceId(ctx), c)) return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("code", c).apply()
        return true
    }
}
