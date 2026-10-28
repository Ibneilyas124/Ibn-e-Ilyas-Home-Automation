package com.ibneilyas.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NodeConfig(val id: String, val room: String, val ip: String, val token: String, val espId: String = "")

class NodeStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("nodes", Context.MODE_PRIVATE)

    var real: Boolean
        get() = p.getBoolean("real", false)
        set(v) { p.edit().putBoolean("real", v).apply() }

    fun load(): List<NodeConfig> {
        val raw = p.getString("list", "[]") ?: "[]"
        val arr = JSONArray(Vault.dec(raw, "[]"))
        if (raw != "[]" && raw.isNotEmpty() && !raw.startsWith("enc:")) p.edit().putString("list", Vault.enc(raw)).apply()
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            NodeConfig(o.getString("id"), o.getString("room"), o.getString("ip"), o.getString("token"), o.optString("eid", ""))
        }
    }

    fun save(list: List<NodeConfig>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("room", it.room).put("ip", it.ip).put("token", it.token).put("eid", it.espId))
        }
        p.edit().putString("list", Vault.enc(arr.toString())).apply()
    }

    var remoteHost: String
        get() = p.getString("rhost", "") ?: ""
        set(v) { p.edit().putString("rhost", v.trim()).apply() }

    var remoteUser: String
        get() = p.getString("ruser", "") ?: ""
        set(v) { p.edit().putString("ruser", v.trim()).apply() }

    var remotePass: String
        get() {
            val r = p.getString("rpass", "") ?: ""
            if (r.isNotEmpty() && !r.startsWith("enc:")) p.edit().putString("rpass", Vault.enc(r)).apply()
            return Vault.dec(r, "")
        }
        set(v) { p.edit().putString("rpass", Vault.enc(v)).apply() }

    /** Customer homes only: the one ESP32 this phone may see on the broker. Empty means owner mode (all). */
    var remoteEsp: String
        get() = p.getString("resp", "") ?: ""
        set(v) { p.edit().putString("resp", v.trim()).apply() }

    fun setEspId(nodeId: String, eid: String) {
        save(load().map { if (it.id == nodeId) it.copy(espId = eid) else it })
    }
}
