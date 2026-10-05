package com.ibneilyas.home.domain

enum class ApplianceType { LIGHT, FAN, SOCKET, OTHER }

data class Room(val id: String, val name: String, val iconKey: String)

data class Node(
    val id: String,
    val name: String,
    val roomId: String,
    val online: Boolean,
    val lastSeenMinutes: Int,
    val channelCount: Int,
    val firmware: String
)

data class Appliance(
    val id: String,
    val name: String,
    val type: ApplianceType,
    val roomId: String,
    val nodeId: String,
    val channel: Int
)

enum class CmdState { IDLE, SENDING, FAILED }

data class ApplianceState(val isOn: Boolean = false, val cmd: CmdState = CmdState.IDLE)

data class HomeData(
    val rooms: List<Room> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val appliances: List<Appliance> = emptyList(),
    val states: Map<String, ApplianceState> = emptyMap(),
    val mockMode: Boolean = true
) {
    fun nodeOf(a: Appliance) = nodes.firstOrNull { it.id == a.nodeId }
    fun isOnline(a: Appliance) = nodeOf(a)?.online == true
    fun stateOf(a: Appliance) = states[a.id] ?: ApplianceState()
    fun devicesIn(roomId: String) = appliances.filter { it.roomId == roomId }
    fun isActive(a: Appliance) = isOnline(a) && stateOf(a).isOn
    fun activeIn(roomId: String) = devicesIn(roomId).count { isActive(it) }
    fun nodeInRoom(roomId: String) = nodes.firstOrNull { it.roomId == roomId } ?: devicesIn(roomId).firstNotNullOfOrNull { nodeOf(it) }
    val activeCount get() = appliances.count { isActive(it) }
    val onlineNodes get() = nodes.count { it.online }
}
