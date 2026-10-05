package com.ibneilyas.home.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.data.*
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.HomeData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

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
        val rooms = d.rooms.map { r -> o.rooms[r.id]?.let { r.copy(name = it) } ?: r }
        val apps = d.appliances.filter { it.id !in o.hidden }.map { a ->
            a.copy(name = o.names[a.id] ?: a.name, type = o.types[a.id] ?: a.type)
        }
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

    fun renameRoom(id: String, name: String) = change { it.copy(rooms = it.rooms + (id to name)) }

    fun editAppliance(id: String, name: String, type: ApplianceType) =
        change { it.copy(names = it.names + (id to name), types = it.types + (id to type)) }

    fun hideAppliance(id: String) = change { it.copy(hidden = it.hidden + id) }

    fun restoreHidden() = change { it.copy(hidden = emptySet()) }
}
