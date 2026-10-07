package com.ibneilyas.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class VoiceLog(val time: Long, val heard: String, val result: String)

/** The last 10 voice commands. Shared by the app and the widget. */
class VoiceLogStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("vlog", Context.MODE_PRIVATE)

    fun load(): List<VoiceLog> {
        val arr = try { JSONArray(p.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            VoiceLog(o.optLong("t"), o.optString("h"), o.optString("r"))
        }
    }

    fun add(e: VoiceLog) {
        val arr = JSONArray()
        (listOf(e) + load()).take(10).forEach {
            arr.put(JSONObject().put("t", it.time).put("h", it.heard).put("r", it.result))
        }
        p.edit().putString("list", arr.toString()).apply()
    }

    fun clear() {
        p.edit().remove("list").apply()
    }
}
