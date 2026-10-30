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
    var lastError: String = ""

    private val http = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
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
        if (com.ibneilyas.home.core.BrandConfig.REQUIRE_GENUINE_CONTROLLER && genuineStatus() != "genuine") return@withContext null
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
        if (com.ibneilyas.home.core.BrandConfig.REQUIRE_GENUINE_CONTROLLER && genuineStatus() != "genuine") return@withContext null
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

    suspend fun info(): String? = raw(req("/api/info").build())

    suspend fun mqttInfo(): String? = raw(req("/api/mqtt").build())

    suspend fun setMqtt(host: String, port: Int, user: String, pass: String): String? =
        text(req("/api/mqtt").post(form("h" to host, "p" to "$port", "u" to user, "w" to pass)).build())

    suspend fun provision(cert: String): String? =
        text(req("/api/provision").post(form("cert" to cert)).build())

    suspend fun setChannels(n: Int): String? =
        text(req("/api/channels").post(form("n" to n.toString())).build())

    suspend fun setConfig(boot: String? = null, sw: Int? = null, maxon: String? = null): String? {
        val parts = ArrayList<Pair<String, String>>()
        if (boot != null) parts.add("boot" to boot)
        if (sw != null) parts.add("sw" to sw.toString()); if (maxon != null) parts.add("maxon" to maxon)
        return text(req("/api/config").post(form(*parts.toTypedArray())).build())
    }

    suspend fun uploadFirmware(bin: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val slow = http.newBuilder()
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
            val bnd = "ibnota" + System.currentTimeMillis()
            val head = ("--" + bnd + "\r\nContent-Disposition: form-data; name=\"update\"; filename=\"app.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray()
            val tail = ("\r\n--" + bnd + "--\r\n").toByteArray()
            val body = (head + bin + tail).toRequestBody(("multipart/form-data; boundary=" + bnd).toMediaType())
            slow.newCall(req("/api/ota").post(body).build()).execute().use { r -> val bd = try { r.body?.string().orEmpty() } catch (e: Exception) { "" }; val why = Regex("\"why\":\"([^\"]*)\"").find(bd)?.groupValues?.get(1).orEmpty(); lastError = "HTTP " + r.code + (if (why.isNotEmpty()) " " + why else ""); r.isSuccessful }
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: ""); false
        }
    }

    /** Asks the ESP32 for a phone seat. Returns (http status, body), or null when it cannot be reached. */
    suspend fun seat(phone: String, max: Int): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            val r = req("/api/seat").post(form("phone" to phone, "max" to "$max")).build()
            http.newCall(r).execute().use { resp -> Pair(resp.code, resp.body?.string().orEmpty()) }
        } catch (e: Exception) {
            null
        }
    }

    @Volatile private var verdict: String? = null
    @Volatile private var checkedAt = 0L

    private suspend fun raw(r: Request): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(r).execute().use { resp -> if (resp.isSuccessful) resp.body!!.string() else null }
        } catch (e: Exception) {
            null
        }
    }

    /** "genuine", "unprovisioned", "oldfw" or "unreachable". A good answer is kept; a bad one is rechecked after 15 seconds. */
    suspend fun genuineStatus(): String {
        val v = verdict
        if (v != null && (v == "genuine" || System.currentTimeMillis() - checkedAt < 15000)) return v
        val info = raw(Request.Builder().url(baseUrl + "/api/info").build()) ?: return "unreachable"
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]*)\"").find(info)?.groupValues?.get(1).orEmpty()
        val certJson = raw(Request.Builder().url(baseUrl + "/api/cert").build())
        val cert = certJson?.let { Regex("\"cert\"\\s*:\\s*\"([^\"]*)\"").find(it)?.groupValues?.get(1) }.orEmpty()
        val ok = com.ibneilyas.home.core.ControllerCert.ok(com.ibneilyas.home.core.LicenseKey.PUBLIC_B64, id, cert)
        val s = when {
            ok -> "genuine"
            certJson == null -> "oldfw"
            else -> "unprovisioned"
        }
        verdict = s
        checkedAt = System.currentTimeMillis()
        return s
    }
}
