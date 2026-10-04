package com.ibneilyas.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.data.HomeRepository
import com.ibneilyas.home.data.MockHomeRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repo: HomeRepository = MockHomeRepository()
    val data = repo.data

    fun toggle(applianceId: String) {
        viewModelScope.launch { repo.toggle(applianceId) }
    }
}
