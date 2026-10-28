package com.ibneilyas.home.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM with a key kept in the Android Keystore. Old plain text values still read fine. */
object Vault {
    private const val ALIAS = "ibn_nodes_key"
    private const val PFX = "enc:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun enc(s: String): String {
        if (s.isEmpty()) return s
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(s.toByteArray(Charsets.UTF_8))
            PFX + Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (e: Exception) {
            s
        }
    }

    fun dec(s: String, def: String): String {
        if (!s.startsWith(PFX)) return s
        return try {
            val b = Base64.decode(s.substring(PFX.length), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            def
        }
    }
}
