package com.ibneilyas.home.data

import com.ibneilyas.home.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MOCK MODE: fake ESP32 nodes. No network, no hardware. */
class MockHomeRepository : HomeRepository {

    private val _data = MutableStateFlow(seed())
    override val data: StateFlow<HomeData> = _data.asStateFlow()

    override suspend fun toggle(applianceId: String) {
        val cur = _data.value
        val app = cur.appliances.firstOrNull { it.id == applianceId } ?: return
        val st = cur.stateOf(app)
        if (st.cmd == CmdState.SENDING) return
        setState(applianceId, st.copy(cmd = CmdState.SENDING))
        delay(600)
        if (cur.isOnline(app)) {
            setState(applianceId, ApplianceState(isOn = !st.isOn, cmd = CmdState.IDLE))
        } else {
            setState(applianceId, st.copy(cmd = CmdState.FAILED))
        }
    }

    private fun setState(id: String, s: ApplianceState) {
        _data.update { it.copy(states = it.states + (id to s)) }
    }

    private fun seed(): HomeData {
        val rooms = listOf(
            Room("r1", "Sarfraz's Room", "bed"),
            Room("r2", "Sheraz's Room", "bed"),
            Room("r3", "Drawing Room", "sofa"),
            Room("r4", "Kitchen", "kitchen")
        )
        val nodes = listOf(
            Node("ESP32-SARFRAZ-ROOM", "Sarfraz Room Controller", "r1", true, 0, 4, "0.1.0"),
            Node("ESP32-SHERAZ-ROOM", "Sheraz Room Controller", "r2", true, 0, 4, "0.1.0"),
            Node("ESP32-DRAWING-ROOM", "Drawing Room Controller", "r3", true, 0, 8, "0.1.0"),
            Node("ESP32-KITCHEN", "Kitchen Controller", "r4", false, 12, 4, "0.1.0")
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
        val states = mapOf(
            "a1" to ApplianceState(true),
            "a3" to ApplianceState(true),
            "a8" to ApplianceState(true),
            "a10" to ApplianceState(true)
        )
        return HomeData(rooms, nodes, apps, states, mockMode = true)
    }
}
