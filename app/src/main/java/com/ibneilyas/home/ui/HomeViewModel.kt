package com.ibneilyas.home.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.data.*
import com.ibneilyas.home.domain.HomeData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NodeStore(app)
    private val repo = MutableStateFlow(build())
    val nodes = MutableStateFlow(store.load())

    @OptIn(ExperimentalCoroutinesApi::class)
    val data = repo.flatMapLatest { it.data }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeData())

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
}
