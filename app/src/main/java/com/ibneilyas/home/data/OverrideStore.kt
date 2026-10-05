package com.ibneilyas.home.data

import android.content.Context
import com.ibneilyas.home.domain.ApplianceType
import org.json.JSONArray
import org.json.JSONObject

/** Names, types and hidden flags the user changed. Applied on top of any data source. */
data class Overrides(
    val names: Map<String, String> = emptyMap(),
    val types: Map<String, ApplianceType> = emptyMap(),
    val rooms: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet()
)

class OverrideStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("overrides", Context.MODE_PRIVATE)

    private fun JSONObject.strMap(key: String): Map<String, String> {
        val o = optJSONObject(key) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun load(): Overrides {
        val j = JSONObject(p.getString("json", "{}") ?: "{}")
        val h = j.optJSONArray("hidden") ?: JSONArray()
        return Overrides(
            names = j.strMap("names"),
            types = j.strMap("types").mapNotNull { (k, v) ->
                runCatching { k to ApplianceType.valueOf(v) }.getOrNull()
            }.toMap(),
            rooms = j.strMap("rooms"),
            hidden = List(h.length()) { h.getString(it) }.toSet()
        )
    }

    fun save(o: Overrides) {
        val j = JSONObject()
        j.put("names", JSONObject(o.names))
        j.put("types", JSONObject(o.types.mapValues { it.value.name }))
        j.put("rooms", JSONObject(o.rooms))
        j.put("hidden", JSONArray(o.hidden.toList()))
        p.edit().putString("json", j.toString()).apply()
    }
}
