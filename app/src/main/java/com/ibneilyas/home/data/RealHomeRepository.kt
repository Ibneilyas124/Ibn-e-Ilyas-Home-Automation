package com.ibneilyas.home.data

import com.ibneilyas.home.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** REAL ESP32 MODE: one node, channels are discovered from the device. */
class RealHomeRepository(
    private val client: HttpNodeClient,
    scope: CoroutineScope
) : HomeRepository {

    private val room = Room("real", "My ESP32", "room")
    private var count = 0
    private var lastOk = 0L
    private val _data = MutableStateFlow(HomeData(mockMode = false))
    override val data: StateFlow<HomeData> = _data.asStateFlow()

    private val job = scope.launch {
        while (true) {
            publish(client.state())
            delay(3000)
        }
    }

    fun close() = job.cancel()

    private fun put(id: String, s: ApplianceState) {
        _data.update { it.copy(states = it.states + (id to s)) }
    }

    private fun publish(list: List<Boolean>?) {
        if (list != null) {
            count = list.size
            lastOk = System.currentTimeMillis()
        }
        val mins = if (lastOk == 0L) 0 else ((System.currentTimeMillis() - lastOk) / 60000).toInt()
        val node = Node("real", "ESP32", "real", list != null, mins, count, "")
        val apps = List(count) {
            Appliance("c${it + 1}", "Channel ${it + 1}", ApplianceType.OTHER, "real", "real", it + 1)
        }
        val old = _data.value.states
        val states = apps.mapIndexed { i, a ->
            val prev = old[a.id]
            val cmd = if (prev?.cmd == CmdState.SENDING) CmdState.SENDING else CmdState.IDLE
            a.id to ApplianceState(list?.get(i) ?: prev?.isOn ?: false, cmd)
        }.toMap()
        _data.value = HomeData(listOf(room), listOf(node), apps, states, false)
    }

    override suspend fun toggle(applianceId: String) {
        val n = applianceId.removePrefix("c").toIntOrNull() ?: return
        val st = _data.value.states[applianceId] ?: ApplianceState()
        if (st.cmd == CmdState.SENDING) return
        put(applianceId, st.copy(cmd = CmdState.SENDING))
        val res = client.setChannel(n, !st.isOn)
        if (res != null) {
            put(applianceId, st.copy(cmd = CmdState.IDLE))
            publish(res)
        } else {
            put(applianceId, st.copy(cmd = CmdState.FAILED))
        }
    }
}
