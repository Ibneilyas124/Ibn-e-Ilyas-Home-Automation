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
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.domain.CmdState
import com.ibneilyas.home.domain.VoiceParser
import com.ibneilyas.home.domain.VoiceResult
import kotlinx.coroutines.withTimeoutOrNull
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
        val apps = d.appliances.filter { it.id !in o.hidden && (!it.spare || it.id in o.activated) }.map { a ->
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
        else MockHomeRepository(getApplication<Application>())
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

    val voiceMessage = MutableStateFlow<String?>(null)
    fun say(s: String) { voiceMessage.value = s }
    fun clearVoice() { voiceMessage.value = null }


    private suspend fun applyVoice(list: List<Appliance>, on: Boolean, label: String): String {
        var done = 0
        var offline = 0
        var failed = 0
        for (a in list) {
            val d = data.value
            if (!d.isOnline(a)) { offline++; continue }
            if (d.stateOf(a).isOn != on) {
                repo.value.toggle(a.id)
                val after = withTimeoutOrNull(5000) { data.first { it.stateOf(a).cmd != CmdState.SENDING } }
                if (after == null || after.stateOf(a).cmd == CmdState.FAILED) { failed++; continue }
            }
            done++
        }
        val v = if (on) "ON" else "OFF"
        return when {
            failed > 0 -> "Could not turn $v $label ($failed failed)"
            done == 0 -> "$label is offline"
            offline > 0 -> "$label $v, $offline offline skipped"
            else -> "$label is now $v"
        }
    }

    val voiceLang = MutableStateFlow(look.getString("vlang", "en") ?: "en")

    fun setVoiceLang(k: String) {
        look.edit().putString("vlang", k).apply()
        voiceLang.value = k
    }

    val voiceRoom = MutableStateFlow<String?>(look.getString("voiceRoom", null))

    fun setVoiceRoom(id: String?) {
        look.edit().putString("voiceRoom", id).apply()
        voiceRoom.value = id
    }

    fun voiceCommand(text: String) {
        viewModelScope.launch {
            val d = data.value
            when (val r = VoiceParser.parse(text, d, scenes.value, voiceRoom.value)) {
                is VoiceResult.Message -> say(r.text)
                is VoiceResult.SelectRoom -> { setVoiceRoom(r.room.id); say("Voice room: ${r.room.name}") }
                is VoiceResult.RunScene -> { runScene(r.s); say("Running ${r.s.name}") }
                is VoiceResult.Device -> {
                    val rn = d.rooms.firstOrNull { it.id == r.a.roomId }?.name
                    say(voiceDevice(r, if (rn != null) "${r.a.name} ($rn)" else r.a.name))
                }
                is VoiceResult.Group -> say(applyVoice(r.list, r.on, r.label))
            }
        }
    }

    suspend fun runVoice(alts: List<String>): String {
        withTimeoutOrNull(4000) { data.first { it.appliances.isNotEmpty() } }
        val d = data.value
        val (r, heard) = VoiceParser.parseBest(alts, d, scenes.value, voiceRoom.value)
        val text = when (r) {
            is VoiceResult.Message -> r.text
            is VoiceResult.SelectRoom -> { setVoiceRoom(r.room.id); "Voice room: ${r.room.name}" }
            is VoiceResult.RunScene -> { runScene(r.s); "Running ${r.s.name}" }
            is VoiceResult.Device -> {
                val rn = d.rooms.firstOrNull { it.id == r.a.roomId }?.name
                voiceDevice(r, if (rn != null) "${r.a.name} ($rn)" else r.a.name)
            }
            is VoiceResult.Group -> applyVoice(r.list, r.on, r.label)
        }
if (speakReplies.value) speaker().speak(spokenText(text))
        logVoice(heard, text)
        return if (text.contains("Heard:") || heard.isEmpty()) text else "$text  [heard: $heard]"
    }

    fun voiceAlternatives(alts: List<String>) {
        viewModelScope.launch { say(runVoice(alts)) }
    }

    /** Returns null when the caller should listen again in the other language. */
    suspend fun voiceTry(alts: List<String>, lang: String, canRetry: Boolean): String? {
        withTimeoutOrNull(4000) { data.first { it.appliances.isNotEmpty() } }
        if (data.value.appliances.isEmpty()) {
            return "No devices available. Check that your ESP32 controllers are online."
        }
        val (r, _) = VoiceParser.parseBest(alts, data.value, scenes.value, voiceRoom.value)
        if (canRetry && VoiceParser.needsRetry(r)) { logVoice(alts.firstOrNull().orEmpty(), "Not understood, trying again"); return null }
        if (r !is VoiceResult.Message) setVoiceLang(lang)
        return runVoice(alts)
    }

    fun storageSummary(): String {
        val o = ostore.load()
        return "Saved: ${o.names.size} renames, ${o.hidden.size} hidden, ${o.customRooms.size} extra rooms, " +
            "${scenes.value.size} scenes, ${store.load().size} ESP32 nodes"
    }

    override fun onCleared() {
        (repo.value as? MultiNodeRepository)?.close()
        speakerOrNull?.shutdown()
        super.onCleared()
    }

    fun freeSlots(): List<com.ibneilyas.home.domain.FreeSlot> {
        val base = repo.value.data.value
        val o = overrides.value
        return base.appliances
            .filter { it.id in o.hidden || (it.spare && it.id !in o.activated) }
            .mapNotNull { a ->
                val n = base.nodes.firstOrNull { it.id == a.nodeId } ?: return@mapNotNull null
                com.ibneilyas.home.domain.FreeSlot(a.id, n.id, n.name, a.channel, n.online)
            }
            .sortedWith(compareBy({ it.nodeName }, { it.channel }))
    }

    fun addAppliance(slotId: String, name: String, type: ApplianceType, roomId: String) = change {
        it.copy(
            names = it.names + (slotId to name),
            types = it.types + (slotId to type),
            applianceRoom = it.applianceRoom + (slotId to roomId),
            hidden = it.hidden - slotId,
            activated = it.activated + slotId
        )
    }

    private val vprefs = app.getSharedPreferences("vcorr", 0)

    private fun loadCorrections(): Map<String, String> {
        val o = JSONObject(vprefs.getString("json", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    val corrections = MutableStateFlow(loadCorrections())

    init {
        com.ibneilyas.home.domain.Lexicon.setExtra(corrections.value)
    }

    private fun saveCorrections(m: Map<String, String>) {
        vprefs.edit().putString("json", JSONObject(m).toString()).apply()
        corrections.value = m
        com.ibneilyas.home.domain.Lexicon.setExtra(m)
    }

    fun addCorrection(heard: String, target: String) {
        val h = heard.trim().lowercase().split(Regex("\\s+")).firstOrNull().orEmpty()
        val t = target.trim().lowercase().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (h.isEmpty() || t.isEmpty() || h == t) return
        saveCorrections(corrections.value + (h to t))
    }

    fun removeCorrection(heard: String) = saveCorrections(corrections.value - heard)

    private var speakerOrNull: Speaker? = null
    private fun speaker(): Speaker = speakerOrNull ?: Speaker(getApplication<Application>()).also { speakerOrNull = it }

    val speakReplies = MutableStateFlow(look.getBoolean("speak", true))
    val preferOffline = MutableStateFlow(look.getBoolean("voffline", false))

    fun setSpeakReplies(v: Boolean) {
        look.edit().putBoolean("speak", v).apply()
        speakReplies.value = v
        if (!v) speakerOrNull?.stop()
    }

    fun setPreferOffline(v: Boolean) {
        look.edit().putBoolean("voffline", v).apply()
        preferOffline.value = v
    }

    private fun spokenText(t: String): String = t
        .replace(Regex("(?s)\\s*\\[heard:.*?\\]"), "")
        .replace(Regex("(?s)\\s*Heard:.*$"), "")
        .replace("(", ", ")
        .replace(")", ",")
        .replace(Regex("\\bON\\b"), "on")
        .replace(Regex("\\bOFF\\b"), "off")
        .trim()

    suspend fun waitSpeech() {
        withTimeoutOrNull(7000) {
            while (speakerOrNull?.speaking == true) kotlinx.coroutines.delay(100)
        }
    }

    private val vlog = VoiceLogStore(app)

    fun recentVoice(): List<VoiceLog> = vlog.load()

    fun clearVoiceLog() = vlog.clear()

    private fun logVoice(heard: String, result: String) {
        if (heard.isNotBlank()) vlog.add(VoiceLog(System.currentTimeMillis(), heard, result))
    }

    /** What a mis-heard word can really mean: words from the user's own rooms and devices. */
    fun teachChoices(): List<Pair<String, List<String>>> {
        val d = data.value
        val seen = HashSet<String>()
        fun fresh(l: List<String>) = l.filter { it.length >= 2 && seen.add(it) }
        val rooms = fresh(d.rooms.flatMap { com.ibneilyas.home.domain.Lexicon.words(it.name) }.filter { it != "room" })
        val devs = fresh(d.appliances.flatMap { com.ibneilyas.home.domain.Lexicon.words(it.name) })
        val acts = fresh(listOf("on", "off"))
        val types = fresh(listOf("light", "bulb", "fan", "socket", "all"))
        return listOf("Room" to rooms, "Device" to devs, "Action" to acts, "Type" to types).filter { it.second.isNotEmpty() }
    }

    private suspend fun voiceDevice(r: VoiceResult.Device, label: String): String {
        if (r.delaySec <= 0) return applyVoice(listOf(r.a), r.on, label)
        val mins = (r.delaySec + 59) / 60
        if (r.forDuration) {
            val first = applyVoice(listOf(r.a), r.on, label)
            if (!first.contains("is now")) return first
            val ok = repo.value.setTimer(r.a.id, r.delaySec, !r.on)
            val then = if (r.on) "off" else "on"
            return if (ok) "$first, then $then in $mins min" else "$first. Timer failed"
        }
        val ok = repo.value.setTimer(r.a.id, r.delaySec, r.on)
        val v = if (r.on) "on" else "off"
        return if (ok) "$label will turn $v in $mins min" else "Could not set the timer. Is the ESP32 online?"
    }

    fun setTimer(applianceId: String, minutes: Int, on: Boolean) {
        viewModelScope.launch {
            val name = data.value.appliances.firstOrNull { it.id == applianceId }?.name ?: "Device"
            val ok = repo.value.setTimer(applianceId, minutes * 60, on)
            val word = if (on) "ON" else "OFF"
            say(
                when {
                    !ok -> "Could not set the timer. Is the ESP32 online?"
                    minutes <= 0 -> "Timer cancelled for $name"
                    else -> "$name will turn $word in $minutes min"
                }
            )
        }
    }

    suspend fun schedulesOf(nodeId: String): List<com.ibneilyas.home.domain.SchedEntry>? =
        repo.value.schedules(nodeId)

    suspend fun saveSchedulesOf(nodeId: String, list: List<com.ibneilyas.home.domain.SchedEntry>): List<com.ibneilyas.home.domain.SchedEntry>? =
        repo.value.saveSchedules(nodeId, list)
}
