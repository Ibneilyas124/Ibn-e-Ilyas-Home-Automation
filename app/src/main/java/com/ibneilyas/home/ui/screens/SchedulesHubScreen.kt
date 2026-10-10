package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.SchedEntry
import com.ibneilyas.home.domain.SchedFormat
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.ScheduleDialog
import kotlinx.coroutines.launch

@Composable
private fun SchedRow(e: SchedEntry, enabled: Boolean, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = enabled && canEdit) { onEdit() }.padding(vertical = 8.dp)) {
            Text((if (e.on) "Turn ON at " else "Turn OFF at ") + SchedFormat.time(e.h, e.m), fontWeight = FontWeight.SemiBold)
            Text(SchedFormat.days(e.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Filled.Delete, contentDescription = "Delete schedule") }
    }
}

@Composable
fun SchedulesHubScreen(roomId: String?, vm: HomeViewModel, onBack: () -> Unit) {
    val d by vm.data.collectAsState()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<Map<String, List<SchedEntry>>>(emptyMap()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SchedEntry?>(null) }
    val room = d.rooms.firstOrNull { it.id == roomId }
    val shown = d.appliances.filter { roomId == null || it.roomId == roomId }
    suspend fun reload() {
        val m = HashMap<String, List<SchedEntry>>()
        var bad = 0
        for (n in d.nodes) {
            val l = vm.schedulesOf(n.id)
            if (l == null) { bad++ } else { m[n.id] = l }
        }
        entries = m
        status = if (bad > 0) "$bad ESP32 not reachable. Their schedules are not shown." else ""
    }
    LaunchedEffect(d.nodes.map { it.id }) { reload() }
    fun without(l: List<SchedEntry>, e: SchedEntry): List<SchedEntry> {
        val c = l.toMutableList()
        c.remove(e)
        return c
    }
    fun saveChange(nodeId: String, mutate: (List<SchedEntry>) -> List<SchedEntry>) {
        scope.launch {
            busy = true
            val cur = vm.schedulesOf(nodeId)
            if (cur == null) {
                status = "Cannot reach this ESP32. Nothing was saved."
            } else {
                val next = mutate(cur)
                if (next.size > 20) {
                    status = "One ESP32 can keep 20 schedules. Delete one first."
                } else {
                    val res = vm.saveSchedulesOf(nodeId, next)
                    if (res == null) {
                        status = "Failed. Nothing was saved."
                    } else {
                        entries = entries + (nodeId to res)
                        status = if (res.size == next.size) "Saved" else "The ESP32 rejected one entry."
                    }
                }
            }
            busy = false
        }
    }
    if (dialog) {
        val old = editing
        val devs = if (old == null) shown else shown.filter { it.nodeId == old.nodeId && it.channel == old.ch }
        ScheduleDialog(
            devices = devs,
            rooms = d.rooms,
            initial = old,
            onDismiss = { dialog = false; editing = null },
            onSave = { dev, onT, offT, days ->
                dialog = false
                editing = null
                val add = ArrayList<SchedEntry>()
                if (onT != null) add.add(SchedEntry(dev.nodeId, onT.first, onT.second, days, dev.channel, true))
                if (offT != null) add.add(SchedEntry(dev.nodeId, offT.first, offT.second, days, dev.channel, false))
                saveChange(dev.nodeId) { cur -> (if (old != null) without(cur, old) else cur) + add }
            }
        )
    }
    val groups = shown.mapNotNull { a ->
        val l = entries[a.nodeId].orEmpty().filter { it.ch == a.channel }.sortedWith(compareBy({ it.h }, { it.m }))
        if (l.isEmpty()) null else Pair(a, l)
    }
    val orphans = if (roomId != null) emptyList<SchedEntry>() else entries.flatMap { (nid, l) ->
        l.filter { e -> d.appliances.none { it.nodeId == nid && it.channel == e.ch } }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(if (room != null) "Schedules - " + room.name else "Schedules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(
                "Schedules run on the ESP32 itself, so they work even when this phone is off. Time is Pakistan time. The ESP32 needs internet once after power-up to know the time.",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (d.mockMode) item { Text("Mock mode: schedules are only saved on this phone as a preview. Nothing runs until you use a real ESP32.", modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary) }
        if (status.isNotEmpty()) item { Text(status, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary) }
        item {
            Button(
                onClick = { editing = null; dialog = true },
                enabled = shown.isNotEmpty() && !busy,
                modifier = Modifier.padding(start = 12.dp).fillMaxWidth().height(56.dp)
            ) { Text("+ Add schedule") }
        }
        if (groups.isEmpty() && orphans.isEmpty()) {
            item { Text("No schedules yet.", modifier = Modifier.padding(start = 12.dp)) }
        }
        items(groups.size) { i ->
            val a = groups[i].first
            val l = groups[i].second
            val rn = d.rooms.firstOrNull { it.id == a.roomId }?.name.orEmpty()
            Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (rn.isNotEmpty()) Text(rn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    l.forEach { e -> SchedRow(e, !busy, true, { editing = e; dialog = true }, { saveChange(e.nodeId) { cur -> without(cur, e) } }) }
                }
            }
        }
        if (orphans.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Unassigned channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        orphans.forEach { e ->
                            Text("Channel " + e.ch, style = MaterialTheme.typography.bodySmall)
                            SchedRow(e, !busy, false, {}, { saveChange(e.nodeId) { cur -> without(cur, e) } })
                        }
                    }
                }
            }
        }
        item { TextButton(onClick = { scope.launch { reload() } }, modifier = Modifier.padding(start = 12.dp)) { Text("Refresh") } }
    }
}
