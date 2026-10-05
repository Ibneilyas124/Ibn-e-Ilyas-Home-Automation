package com.ibneilyas.home.data

import android.content.Context
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.Room
import org.json.JSONArray
import org.json.JSONObject

data class Overrides(
    val names: Map<String, String> = emptyMap(),
    val types: Map<String, ApplianceType> = emptyMap(),
    val rooms: Map<String, String> = emptyMap(),
    val roomIcons: Map<String, String> = emptyMap(),
    val applianceRoom: Map<String, String> = emptyMap(),
    val customRooms: List<Room> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val hiddenRooms: Set<String> = emptySet()
)

class OverrideStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("overrides", Context.MODE_PRIVATE)

    private fun JSONObject.strMap(key: String): Map<String, String> {
        val o = optJSONObject(key) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    private fun JSONObject.strSet(key: String): Set<String> {
        val a = optJSONArray(key) ?: return emptySet()
        return List(a.length()) { a.getString(it) }.toSet()
    }

    fun load(): Overrides {
        val j = JSONObject(p.getString("json", "{}") ?: "{}")
        val cr = j.optJSONArray("customRooms") ?: JSONArray()
        return Overrides(
            names = j.strMap("names"),
            types = j.strMap("types").mapNotNull { (k, v) ->
                runCatching { k to ApplianceType.valueOf(v) }.getOrNull()
            }.toMap(),
            rooms = j.strMap("rooms"),
            roomIcons = j.strMap("roomIcons"),
            applianceRoom = j.strMap("applianceRoom"),
            customRooms = List(cr.length()) {
                val o = cr.getJSONObject(it)
                Room(o.getString("id"), o.getString("name"), o.getString("icon"))
            },
            hidden = j.strSet("hidden"),
            hiddenRooms = j.strSet("hiddenRooms")
        )
    }

    fun save(o: Overrides) {
        val j = JSONObject()
        j.put("names", JSONObject(o.names))
        j.put("types", JSONObject(o.types.mapValues { it.value.name }))
        j.put("rooms", JSONObject(o.rooms))
        j.put("roomIcons", JSONObject(o.roomIcons))
        j.put("applianceRoom", JSONObject(o.applianceRoom))
        val cr = JSONArray()
        o.customRooms.forEach {
            cr.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.iconKey))
        }
        j.put("customRooms", cr)
        j.put("hidden", JSONArray(o.hidden.toList()))
        j.put("hiddenRooms", JSONArray(o.hiddenRooms.toList()))
        p.edit().putString("json", j.toString()).apply()
    }
}
