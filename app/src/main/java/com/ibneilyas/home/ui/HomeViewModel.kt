package com.ibneilyas.home.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.data.*
import com.ibneilyas.home.domain.HomeData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConnectionStore(app)
    private val repo = MutableStateFlow(build())

    @OptIn(ExperimentalCoroutinesApi::class)
    val data = repo.flatMapLatest { it.data }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeData())

    private fun build(): HomeRepository =
        if (store.real && store.ip.isNotBlank() && store.token.isNotBlank())
            RealHomeRepository(HttpNodeClient("http://${store.ip}", store.token), viewModelScope)
        else MockHomeRepository()

    private fun swap() {
        (repo.value as? RealHomeRepository)?.close()
        repo.value = build()
    }

    fun savedIp(): String = store.ip

    fun connect(ip: String, token: String) {
        store.ip = ip.trim().removePrefix("http://").trimEnd('/')
        store.token = token.trim()
        store.real = true
        swap()
    }

    fun useMock() {
        store.real = false
        swap()
    }

    fun toggle(applianceId: String) {
        viewModelScope.launch { repo.value.toggle(applianceId) }
    }
}
