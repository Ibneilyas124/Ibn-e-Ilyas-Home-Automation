package com.ibneilyas.home.data

import android.content.Context
import android.content.SharedPreferences
import com.ibneilyas.home.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MOCK MODE: fake ESP32 nodes. Light states are saved, so they survive restarts and are shared with the widget. */
class MockHomeRepository(ctx: Context) : HomeRepository {
    private val prefs = ctx.getSharedPreferences("mockstate", Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(seed())
    override val data: StateFlow<HomeData> = _data.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "on") reload()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override suspend fun toggle(applianceId: String) {
        val cur = _data.value
        val app = cur.appliances.firstOrNull { it.id == applianceId } ?: return
        val st = cur.stateOf(app)
        if (st.cmd == CmdState.SENDING) return
        setState(applianceId, st.copy(cmd = CmdState.SENDING))
        delay(600)
        if (cur.isOnline(app)) {
            setState(applianceId, ApplianceState(isOn = !st.isOn, cmd = CmdState.IDLE))
            save()
        } else {
            setState(applianceId, st.copy(cmd = CmdState.FAILED))
        }
    }

    private fun setState(id: String, s: ApplianceState) {
        _data.update { it.copy(states = it.states + (id to s)) }
    }

    private fun onIds(): Set<String> =
        prefs.getString("on", null)?.split(",")?.filter { it.isNotEmpty() }?.toSet()
            ?: setOf("a1", "a3", "a8", "a10")

    private fun save() {
        val on = _data.value.states.filter { it.value.isOn }.keys.joinToString(",")
        prefs.edit().putString("on", on).apply()
    }

    private fun reload() {
        val ids = onIds()
        _data.update { d ->
            d.copy(states = d.appliances.associate { a ->
                a.id to ApplianceState(a.id in ids, d.states[a.id]?.cmd ?: CmdState.IDLE)
            })
        }
    }

    private fun seed(): HomeData {
        val rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Sheraz's Room", "bed"),
            Room("r3", "Drawing Room", "sofa"),
            Room("r4", "Kitchen", "kitchen")
        )
        val nodes = listOf(
            Node("ESP32-SARFRAZ-ROOM", "Sarfraz Room Controller", "r1", true, 0, 8, "0.1.0"),
            Node("ESP32-SHERAZ-ROOM", "Sheraz Room Controller", "r2", true, 0, 8, "0.1.0"),
            Node("ESP32-DRAWING-ROOM", "Drawing Room Controller", "r3", true, 0, 16, "0.1.0"),
            Node("ESP32-KITCHEN", "Kitchen Controller", "r4", false, 12, 8, "0.1.0")
        )
        val apps = listOf(
            Appliance("a1", "Living Light", ApplianceType.LIGHT, "r1", "ESP32-SARFRAZ-ROOM", 1),
            Appliance("a2", "Ceiling Fan", ApplianceType.FAN, "r1", "ESP32-SARFRAZ-ROOM", 2),
            Appliance("a3", "Bed Light", ApplianceType.LIGHT, "r1", "ESP32-SARFRAZ-ROOM", 3),
            Appliance("a4", "Power Socket", ApplianceType.SOCKET, "r1", "ESP32-SARFRAZ-ROOM", 4),
            Appliance("a5", "Room Light", ApplianceType.LIGHT, "r2", "ESP32-SHERAZ-ROOM", 1),
            Appliance("a6", "Ceiling Fan", ApplianceType.FAN, "r2", "ESP32-SHERAZ-ROOM", 2),
            Appliance("a7", "Power Socket", ApplianceType.SOCKET, "r2", "ESP32-SHERAZ-ROOM", 3),
            Appliance("a8", "Main Light", ApplianceType.LIGHT, "r3", "ESP32-DRAWING-ROOM", 1),
            Appliance("a9", "Ceiling Fan", ApplianceType.FAN, "r3", "ESP32-DRAWING-ROOM", 2),
            Appliance("a10", "Kitchen Light", ApplianceType.LIGHT, "r4", "ESP32-KITCHEN", 1),
            Appliance("a11", "Exhaust Fan", ApplianceType.FAN, "r4", "ESP32-KITCHEN", 2)
        )
        val used = apps.map { it.nodeId to it.channel }.toSet()
        val spares = nodes.flatMap { n ->
            (1..n.channelCount).filter { (n.id to it) !in used }.map { ch ->
                Appliance("sp-${n.id}-$ch", "Channel $ch", ApplianceType.OTHER, n.roomId, n.id, ch, true)
            }
        }
        val all = apps + spares
        val ids = onIds()
        val states = all.associate { it.id to ApplianceState(it.id in ids) }
        return HomeData(rooms, nodes, all, states, mockMode = true)
    }

    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timerJobs = HashMap<String, Job>()

    override suspend fun setTimer(applianceId: String, seconds: Int, on: Boolean): Boolean {
        timerJobs.remove(applianceId)?.cancel()
        if (seconds <= 0) return true
        timerJobs[applianceId] = timerScope.launch {
            delay(seconds * 1000L)
            val st = _data.value.states[applianceId] ?: return@launch
            if (st.isOn != on) toggle(applianceId)
        }
        return true
    }
}
