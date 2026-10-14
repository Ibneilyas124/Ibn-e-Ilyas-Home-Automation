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
        return (code.isNotEmpty() && verify(LicenseKey.PUBLIC_B64, deviceId(ctx), code)) || houseOf(ctx) != null
    }

    fun activate(ctx: Context, code: String): Boolean {
        val c = code.filter { !it.isWhitespace() }
        if (!verify(LicenseKey.PUBLIC_B64, deviceId(ctx), c)) return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("code", c).apply()
        return true
    }

    fun parseHousehold(pubB64: String, code: String): Household? {
        try {
            val parts = code.filter { !it.isWhitespace() }.split(".")
            if (parts.size != 3 || parts[0] != "K2") return null
            val dec = Base64.getDecoder()
            val payload = dec.decode(parts[1])
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(dec.decode(pubB64.filter { !it.isWhitespace() })))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(key)
            sig.update(payload)
            if (!sig.verify(dec.decode(parts[2]))) return null
            val f = String(payload, Charsets.UTF_8).split("|")
            if (f.size < 4 || f[0] != "IBN2") return null
            val max = f[2].toIntOrNull() ?: return null
            if (max < 1 || max > 50) return null
            return Household(f[1], max, f.drop(3).joinToString("|"))
        } catch (e: Exception) {
            return null
        }
    }

    fun houseOf(ctx: Context): Household? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val k = p.getString("house", "") ?: ""
        if (k.isEmpty() || p.getString("seat", "") != deviceId(ctx)) return null
        return parseHousehold(LicenseKey.PUBLIC_B64, k)
    }

    fun saveHousehold(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("house", key.filter { !it.isWhitespace() })
            .putString("seat", deviceId(ctx))
            .apply()
    }
}

data class Household(val espId: String, val max: Int, val name: String)
