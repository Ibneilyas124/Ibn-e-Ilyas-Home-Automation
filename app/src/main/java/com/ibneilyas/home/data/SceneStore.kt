package com.ibneilyas.home.data

import android.content.Context
import com.ibneilyas.home.domain.Scene
import com.ibneilyas.home.domain.SceneAction
import org.json.JSONArray
import org.json.JSONObject

class SceneStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("scenes", Context.MODE_PRIVATE)

    fun load(): List<Scene> {
        val arr = JSONArray(p.getString("list", "[]"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            val a = o.getJSONArray("actions")
            Scene(o.getString("id"), o.getString("name"), List(a.length()) { i ->
                val x = a.getJSONObject(i)
                SceneAction(x.getString("a"), x.getBoolean("on"))
            })
        }
    }

    fun save(list: List<Scene>) {
        val arr = JSONArray()
        list.forEach { s ->
            val acts = JSONArray()
            s.actions.forEach { acts.put(JSONObject().put("a", it.applianceId).put("on", it.on)) }
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("actions", acts))
        }
        p.edit().putString("list", arr.toString()).apply()
    }
}
