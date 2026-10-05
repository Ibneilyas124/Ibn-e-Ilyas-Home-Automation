package com.ibneilyas.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NodeConfig(val id: String, val room: String, val ip: String, val token: String)

class NodeStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("nodes", Context.MODE_PRIVATE)

    var real: Boolean
        get() = p.getBoolean("real", false)
        set(v) { p.edit().putBoolean("real", v).apply() }

    fun load(): List<NodeConfig> {
        val arr = JSONArray(p.getString("list", "[]"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            NodeConfig(o.getString("id"), o.getString("room"), o.getString("ip"), o.getString("token"))
        }
    }

    fun save(list: List<NodeConfig>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("room", it.room).put("ip", it.ip).put("token", it.token))
        }
        p.edit().putString("list", arr.toString()).apply()
    }
}
