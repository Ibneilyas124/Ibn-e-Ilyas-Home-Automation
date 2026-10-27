package com.ibneilyas.home.data

import com.ibneilyas.home.core.BrandConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Customer setup: one activation key makes the home in the cloud, then the ESP32 gets its broker settings. */
object CloudActivation {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val keyRe = Regex("^IBN-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$")
    private val idRe = Regex("\"id\"\\s*:\\s*\"([^\"]*)\"")
    private val upRe = Regex("\"connected\"\\s*:\\s*true")

    private fun clientFor(ip: String, token: String): HttpNodeClient =
        HttpNodeClient("http://" + ip.trim().removePrefix("http://").trimEnd('/'), token.trim())

    private suspend fun controllerId(client: HttpNodeClient): String? =
        client.info()?.let { idRe.find(it)?.groupValues?.get(1) }?.takeIf { it.isNotEmpty() }

    private suspend fun ask(key: String, esp: String, token: String): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("key", key).put("esp", esp).put("token", token).toString()
                .toRequestBody("application/json".toMediaType())
            val r = Request.Builder().url(BrandConfig.CLOUD_URL + "/home/activate").post(body).build()
            http.newCall(r).execute().use { resp ->
                val o = try { JSONObject(resp.body?.string().orEmpty()) } catch (e: Exception) { null }
                Pair(resp.code, o)
            }
        } catch (e: Exception) {
            Pair(0, null)
        }
    }

    /** Returns null when the home is ready, or a message to show the user. */
    suspend fun run(store: NodeStore, key: String, ip: String, token: String): String? {
        val k = key.trim().uppercase()
        if (!keyRe.matches(k)) return "This activation key is not valid."
        val client = clientFor(ip, token)
        val id = controllerId(client) ?: return "Cannot reach the controller. Join the home Wi-Fi and check the IP address."
        if (client.state() == null) return "Wrong controller token."
        val (code, o) = ask(k, id, token.trim())
        if (code != 200 || o == null || !o.optBoolean("ok")) {
            return when (o?.optString("error")) {
                "invalid_key" -> "This activation key is not valid or was already used."
                "locked" -> "Too many wrong tries. Wait one hour and try again."
                "esp_taken" -> "This controller already belongs to another home."
                "bad_request" -> "The key or the controller token was not accepted."
                else -> if (code == 0) "Cannot reach the cloud. Check the phone's internet." else "Cloud setup failed (code " + code + "). Try again later."
            }
        }
        val host = o.optString("host")
        val user = o.optString("user")
        val pass = o.optString("pass")
        if (host.isBlank() || user.isBlank() || pass.isEmpty() || o.optString("esp") != id) return "The cloud answer was not complete. Try again later."
        store.remoteHost = host
        store.remoteUser = user
        store.remotePass = pass
        store.remoteEsp = id
        return push(store, client)
    }

    /** Sends the saved cloud settings to the controller again. No new key is needed. */
    suspend fun resend(store: NodeStore, ip: String, token: String): String? {
        if (store.remoteEsp.isBlank() || store.remoteHost.isBlank() || store.remoteUser.isBlank() || store.remotePass.isEmpty()) {
            return "Nothing is saved yet. Use an activation key first."
        }
        val client = clientFor(ip, token)
        val id = controllerId(client) ?: return "Cannot reach the controller. Join the home Wi-Fi and check the IP address."
        if (id != store.remoteEsp) return "These cloud settings belong to another controller (" + store.remoteEsp + ")."
        return push(store, client)
    }

    private suspend fun askPhone(esp: String, token: String): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("esp", esp).put("token", token).toString()
                .toRequestBody("application/json".toMediaType())
            val r = Request.Builder().url(BrandConfig.CLOUD_URL + "/home/addphone").post(body).build()
            http.newCall(r).execute().use { resp ->
                val o = try { JSONObject(resp.body?.string().orEmpty()) } catch (e: Exception) { null }
                Pair(resp.code, o)
            }
        } catch (e: Exception) {
            Pair(0, null)
        }
    }

    /** Extra phone of a home that is already set up: no key, only the controller token. Does not change the controller's own cloud settings. */
    suspend fun addPhone(store: NodeStore, ip: String, token: String): String? {
        val client = clientFor(ip, token)
        val id = controllerId(client) ?: return "Cannot reach the controller. Join the home Wi-Fi and check the IP address."
        if (client.state() == null) return "Wrong controller token."
        val (code, o) = askPhone(id, token.trim())
        if (code != 200 || o == null || !o.optBoolean("ok")) {
            return when (o?.optString("error")) {
                "invalid" -> "This controller is not set up for remote yet, or the token is wrong."
                "locked" -> "Too many wrong tries. Wait one hour and try again."
                "phone_limit" -> "This home already has the maximum number of phones."
                "bad_request" -> "The controller token was not accepted."
                else -> if (code == 0) "Cannot reach the cloud. Check the phone's internet." else "Cloud setup failed (code " + code + "). Try again later."
            }
        }
        val host = o.optString("host")
        val user = o.optString("user")
        val pass = o.optString("pass")
        if (host.isBlank() || user.isBlank() || pass.isEmpty() || o.optString("esp") != id) return "The cloud answer was not complete. Try again later."
        store.remoteHost = host
        store.remoteUser = user
        store.remotePass = pass
        store.remoteEsp = id
        return null
    }

    private suspend fun push(store: NodeStore, client: HttpNodeClient): String? {
        val sent = client.setMqtt(store.remoteHost, 8883, store.remoteUser, store.remotePass)
        if (sent == null) {
            return "Saved on this phone, but the controller did not accept the settings. Join the home Wi-Fi and try again. No new key is needed."
        }
        repeat(8) {
            val m = client.mqttInfo()
            if (m != null && upRe.containsMatchIn(m)) return null
            delay(1000)
        }
        return "Settings sent, but the controller is not connected to the cloud yet. Check its internet and try again in a minute."
    }
}
