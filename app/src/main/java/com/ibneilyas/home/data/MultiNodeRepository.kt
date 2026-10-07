package com.ibneilyas.home.data

import com.ibneilyas.home.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** REAL MODE: many ESP32 nodes. One room per node, channels come from the device. */
class MultiNodeRepository(
    private val configs: List<NodeConfig>,
    scope: CoroutineScope
) : HomeRepository {

    private data class Live(
        val online: Boolean = false,
        val ch: List<Boolean> = emptyList(),
        val lastOk: Long = 0L
    )

    private val clients = configs.associate { it.id to HttpNodeClient("http://${it.ip}", it.token) }
    private val live = mutableMapOf<String, Live>()
    private val cmd = mutableMapOf<String, CmdState>()
    private val _data = MutableStateFlow(HomeData(mockMode = false))
    override val data: StateFlow<HomeData> = _data.asStateFlow()

    init { publish() }

    private val hub = SocketHub(configs) { id, list -> scope.launch { refresh(id, list) } }
    private val jobs = configs.map { c ->
        scope.launch {
            while (true) {
                hub.ensure(c.id)
                refresh(c.id, clients.getValue(c.id).state())
                delay(if (hub.isOpen(c.id)) 8000 else 3000)
            }
        }
    }

    fun close() {
        jobs.forEach { it.cancel() }
        hub.closeAll()
    }

    private fun refresh(id: String, list: List<Boolean>?) {
        val old = live[id] ?: Live()
        live[id] = if (list != null) Live(true, list, System.currentTimeMillis())
                   else old.copy(online = false)
        if (list != null) {
            cmd.entries.removeAll { it.key.startsWith("$id:") && it.value == CmdState.FAILED }
        }
        publish()
    }

    private fun publish() {
        val rooms = configs.map { Room(it.id, it.room, "room") }
        val nodes = configs.map {
            val l = live[it.id] ?: Live()
            val mins = if (l.lastOk == 0L) 0 else ((System.currentTimeMillis() - l.lastOk) / 60000).toInt()
            Node(it.id, it.room, it.id, l.online, mins, l.ch.size, "")
        }
        val apps = configs.flatMap { c ->
            val n = live[c.id]?.ch?.size ?: 0
            List(n) { Appliance("${c.id}:${it + 1}", "Channel ${it + 1}", ApplianceType.OTHER, c.id, c.id, it + 1) }
        }
        val states = apps.associate { a ->
            val on = live[a.nodeId]?.ch?.getOrNull(a.channel - 1) ?: false
            a.id to ApplianceState(on, cmd[a.id] ?: CmdState.IDLE)
        }
        _data.value = HomeData(rooms, nodes, apps, states, false)
    }

    override suspend fun toggle(applianceId: String) {
        val id = applianceId.substringBefore(':')
        val ch = applianceId.substringAfter(':').toIntOrNull() ?: return
        val client = clients[id] ?: return
        if (cmd[applianceId] == CmdState.SENDING) return
        val on = live[id]?.ch?.getOrNull(ch - 1) ?: false
        cmd[applianceId] = CmdState.SENDING
        publish()
        val res = client.setChannel(ch, !on)
        cmd[applianceId] = if (res != null) CmdState.IDLE else CmdState.FAILED
        if (res != null) refresh(id, res) else publish()
    }

    override suspend fun setTimer(applianceId: String, seconds: Int, on: Boolean): Boolean {
        val id = applianceId.substringBefore(':')
        val ch = applianceId.substringAfter(':').toIntOrNull() ?: return false
        val client = clients[id] ?: return false
        return client.setTimer(ch, seconds, on) != null
    }
}
