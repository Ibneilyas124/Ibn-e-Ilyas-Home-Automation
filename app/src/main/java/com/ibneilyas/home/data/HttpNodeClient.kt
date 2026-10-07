package com.ibneilyas.home.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** REAL ESP32 MODE: talks to one node over the local network. */
class HttpNodeClient(private val baseUrl: String, private val token: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private fun req(path: String) =
        Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")

    /** Channel states, or null if node is unreachable / unauthorized. */
    suspend fun state(): List<Boolean>? = call(req("/api/state").build())

    suspend fun setChannel(n: Int, on: Boolean): List<Boolean>? {
        val body = "on=${if (on) 1 else 0}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        return call(req("/api/channel/$n").post(body).build())
    }

    private suspend fun call(r: Request): List<Boolean>? = withContext(Dispatchers.IO) {
        try {
            http.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONObject(resp.body!!.string()).getJSONArray("channels")
                List(arr.length()) { arr.getBoolean(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun text(r: Request): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(r).execute().use { resp ->
                if (resp.isSuccessful) resp.body!!.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun schedules(): String? = text(req("/api/schedules").build())

    suspend fun setSchedules(data: String): String? {
        val body = "data=${java.net.URLEncoder.encode(data, "UTF-8")}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        return text(req("/api/schedules").post(body).build())
    }

    suspend fun time(): String? = text(req("/api/time").build())

    private fun form(vararg p: Pair<String, String>) =
        p.joinToString("&") { it.first + "=" + java.net.URLEncoder.encode(it.second, "UTF-8") }
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

    suspend fun setTimer(ch: Int, sec: Int, on: Boolean): String? =
        text(req("/api/timer").post(form("ch" to "$ch", "sec" to "$sec", "on" to (if (on) "1" else "0"))).build())

    suspend fun timers(): String? = text(req("/api/timers").build())

    suspend fun events(): String? = text(req("/api/events").build())

    suspend fun config(): String? = text(req("/api/config").build())

    suspend fun info(): String? = text(req("/api/info").build())

    suspend fun setConfig(boot: String? = null, sw: Int? = null): String? {
        val parts = ArrayList<Pair<String, String>>()
        if (boot != null) parts.add("boot" to boot)
        if (sw != null) parts.add("sw" to sw.toString())
        return text(req("/api/config").post(form(*parts.toTypedArray())).build())
    }

    suspend fun uploadFirmware(bin: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val slow = http.newBuilder()
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("update", "app.bin", bin.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            slow.newCall(req("/api/ota").post(body).build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
