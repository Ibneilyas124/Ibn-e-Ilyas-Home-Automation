package com.ibneilyas.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibneilyas.home.ui.HomeViewModel
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import com.ibneilyas.home.ui.navigation.AppNav
import com.ibneilyas.home.ui.theme.HomeTheme

class MainActivity : ComponentActivity() {
    // Ghar aate hi notification tap: Scene chalao (devices online hone ke intezar ke baad)
    private fun handleGeo(i: Intent?) {
        if (i == null) return
        val id = i.getStringExtra("geoScene") ?: return
        i.removeExtra("geoScene")
        ViewModelProvider(this)[HomeViewModel::class.java].runSceneWhenReady(id)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGeo(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleGeo(intent)
        setContent {
            val vm: HomeViewModel = viewModel()
            val dark by vm.dark.collectAsState()
            val accent by vm.accent.collectAsState()
            HomeTheme(dark, accent) { SelectionContainer { AppNav() } }
        }
    }
}
