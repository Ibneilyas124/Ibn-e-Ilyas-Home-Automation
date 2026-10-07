package com.ibneilyas.home.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Firmware files published by GitHub Pages (built from the main branch by the Build Firmware workflow). */
object FirmwareHub {
    private const val BASE = "https://ibneilyas124.github.io/Ibn-e-Ilyas-Home-Automation/"
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun latestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val r = Request.Builder().url(BASE + "version.json?t=" + System.currentTimeMillis()).build()
            http.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body!!.string()).getString("version")
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads app.bin. Refuses anything that does not look like an ESP32 app image. */
    suspend fun download(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val r = Request.Builder().url(BASE + "app.bin?t=" + System.currentTimeMillis()).build()
            http.newCall(r).execute().use { resp ->
                val b = if (resp.isSuccessful) resp.body!!.bytes() else null
                if (b != null && b.size > 200000 && b[0] == 0xE9.toByte()) b else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun newer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
