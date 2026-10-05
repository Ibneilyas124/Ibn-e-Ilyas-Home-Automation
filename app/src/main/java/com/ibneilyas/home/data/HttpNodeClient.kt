package com.ibneilyas.home.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
}
