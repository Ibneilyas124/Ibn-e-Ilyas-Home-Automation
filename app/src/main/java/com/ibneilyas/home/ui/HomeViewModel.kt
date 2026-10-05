package com.ibneilyas.home.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.data.*
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.HomeData
import com.ibneilyas.home.domain.Room
import com.ibneilyas.home.domain.Scene
import com.ibneilyas.home.domain.SceneAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import com.ibneilyas.home.core.BrandConfig

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NodeStore(app)
    private val ostore = OverrideStore(app)
    private val repo = MutableStateFlow(build())
    private val overrides = MutableStateFlow(ostore.load())
    val nodes = MutableStateFlow(store.load())

    @OptIn(ExperimentalCoroutinesApi::class)
    val data = repo.flatMapLatest { it.data }
        .combine(overrides) { d, o -> withOverrides(d, o) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeData())

    private fun withOverrides(d: HomeData, o: Overrides): HomeData {
        val rooms = (d.rooms + o.customRooms).filter { it.id !in o.hiddenRooms }.map { r ->
            r.copy(name = o.rooms[r.id] ?: r.name, iconKey = o.roomIcons[r.id] ?: r.iconKey)
        }
        val apps = d.appliances.filter { it.id !in o.hidden }.map { a ->
            a.copy(
                name = o.names[a.id] ?: a.name,
                type = o.types[a.id] ?: a.type,
                roomId = o.applianceRoom[a.id] ?: a.roomId
            )
        }.filter { a -> rooms.any { it.id == a.roomId } }
        return d.copy(rooms = rooms, appliances = apps)
    }

    private fun build(): HomeRepository {
        val list = store.load()
        return if (store.real && list.isNotEmpty()) MultiNodeRepository(list, viewModelScope)
        else MockHomeRepository()
    }

    private fun swap() {
        (repo.value as? MultiNodeRepository)?.close()
        nodes.value = store.load()
        repo.value = build()
    }

    fun addNode(room: String, ip: String, token: String) {
        val c = NodeConfig(
            UUID.randomUUID().toString().take(8),
            room.trim(),
            ip.trim().removePrefix("http://").trimEnd('/'),
            token.trim()
        )
        store.save(store.load() + c)
        store.real = true
        swap()
    }

    fun removeNode(id: String) {
        store.save(store.load().filter { it.id != id })
        swap()
    }

    fun useMock() { store.real = false; swap() }

    fun useReal() { store.real = true; swap() }

    fun toggle(applianceId: String) {
        viewModelScope.launch { repo.value.toggle(applianceId) }
    }

    private fun change(f: (Overrides) -> Overrides) {
        val n = f(overrides.value)
        ostore.save(n)
        overrides.value = n
    }

    fun addRoom(name: String, icon: String) = change {
        it.copy(customRooms = it.customRooms + Room("custom-" + UUID.randomUUID().toString().take(6), name, icon))
    }

    fun editRoom(id: String, name: String, icon: String) =
        change { it.copy(rooms = it.rooms + (id to name), roomIcons = it.roomIcons + (id to icon)) }

    fun deleteRoom(id: String) = change { o ->
        if (o.customRooms.any { it.id == id }) {
            o.copy(
                customRooms = o.customRooms.filter { it.id != id },
                applianceRoom = o.applianceRoom.filterValues { it != id }
            )
        } else {
            o.copy(hiddenRooms = o.hiddenRooms + id)
        }
    }

    fun editAppliance(id: String, name: String, type: ApplianceType, roomId: String) = change {
        it.copy(
            names = it.names + (id to name),
            types = it.types + (id to type),
            applianceRoom = it.applianceRoom + (id to roomId)
        )
    }

    fun hideAppliance(id: String) = change { it.copy(hidden = it.hidden + id) }

    fun restoreHidden() = change { it.copy(hidden = emptySet(), hiddenRooms = emptySet()) }

    private val sstore = SceneStore(app)
    val scenes = MutableStateFlow(sstore.load())
    val runningScene = MutableStateFlow<String?>(null)
    val sceneMessage = MutableStateFlow<String?>(null)

    fun addScene(name: String, actions: List<SceneAction>) {
        val n = scenes.value + Scene("s-" + UUID.randomUUID().toString().take(6), name, actions)
        sstore.save(n)
        scenes.value = n
    }

    fun deleteScene(id: String) {
        val n = scenes.value.filter { it.id != id }
        sstore.save(n)
        scenes.value = n
    }

    fun runScene(scene: Scene) {
        if (runningScene.value != null) return
        viewModelScope.launch {
            runningScene.value = scene.id
            sceneMessage.value = null
            var skipped = 0
            for (a in scene.actions) {
                val d = data.value
                val app = d.appliances.firstOrNull { it.id == a.applianceId } ?: continue
                if (!d.isOnline(app)) { skipped++; continue }
                if (d.stateOf(app).isOn != a.on) repo.value.toggle(app.id)
            }
            runningScene.value = null
            sceneMessage.value =
                if (skipped > 0) "${scene.name}: done, $skipped offline device(s) skipped"
                else "${scene.name}: done"
        }
    }

    private val brand = app.getSharedPreferences("brand", 0)
    val subtitle = MutableStateFlow(brand.getString("subtitle", null) ?: BrandConfig.DEFAULT_SUBTITLE)

    fun setSubtitle(s: String) {
        val v = s.trim().ifBlank { BrandConfig.DEFAULT_SUBTITLE }
        brand.edit().putString("subtitle", v).apply()
        subtitle.value = v
    }

    fun setToken(id: String, token: String) {
        store.save(store.load().map { if (it.id == id) it.copy(token = token.trim()) else it })
        swap()
    }

    fun exportJson(): String {
        val ctx = getApplication<Application>()
        val j = JSONObject()
        j.put("app", "IbnEIlyasHome")
        j.put("version", 1)
        j.put("subtitle", subtitle.value)
        val na = JSONArray()
        store.load().forEach { na.put(JSONObject().put("id", it.id).put("room", it.room).put("ip", it.ip)) }
        j.put("nodes", na)
        j.put("overrides", JSONObject(ctx.getSharedPreferences("overrides", 0).getString("json", "{}") ?: "{}"))
        j.put("scenes", JSONArray(ctx.getSharedPreferences("scenes", 0).getString("list", "[]") ?: "[]"))
        return j.toString(2)
    }

    fun importJson(text: String): String {
        val ctx = getApplication<Application>()
        val op = ctx.getSharedPreferences("overrides", 0)
        val sp = ctx.getSharedPreferences("scenes", 0)
        val oldO = op.getString("json", "{}") ?: "{}"
        val oldS = sp.getString("list", "[]") ?: "[]"
        try {
            val j = JSONObject(text)
            if (j.optString("app") != "IbnEIlyasHome" || j.optInt("version", 0) != 1) {
                return "Not a valid backup file"
            }
            val old = store.load().associateBy { it.id }
            val na = j.getJSONArray("nodes")
            val list = List(na.length()) {
                val o = na.getJSONObject(it)
                val id = o.getString("id")
                NodeConfig(id, o.getString("room"), o.getString("ip"), old[id]?.token ?: "")
            }
            op.edit().putString("json", j.getJSONObject("overrides").toString()).apply()
            sp.edit().putString("list", j.getJSONArray("scenes").toString()).apply()
            val o2 = ostore.load()
            val s2 = sstore.load()
            store.save(list)
            overrides.value = o2
            scenes.value = s2
            setSubtitle(j.optString("subtitle", ""))
            swap()
            val missing = list.count { it.token.isBlank() }
            return if (missing > 0) "Restored. Set token for $missing ESP32 in Devices." else "Restored."
        } catch (e: Exception) {
            op.edit().putString("json", oldO).apply()
            sp.edit().putString("list", oldS).apply()
            return "Backup file is damaged. Nothing was changed."
        }
    }

    private val look = app.getSharedPreferences("look", 0)
    val dark = MutableStateFlow(look.getBoolean("dark", true))
    val accent = MutableStateFlow(look.getString("accent", "blue") ?: "blue")

    fun setDark(v: Boolean) {
        look.edit().putBoolean("dark", v).apply()
        dark.value = v
    }

    fun setAccent(k: String) {
        look.edit().putString("accent", k).apply()
        accent.value = k
    }
}
