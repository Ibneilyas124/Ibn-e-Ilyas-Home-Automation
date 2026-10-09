package com.ibneilyas.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibneilyas.home.core.License
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.navigation.AppNav
import com.ibneilyas.home.ui.screens.ActivationScreen
import com.ibneilyas.home.ui.theme.HomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: HomeViewModel = viewModel()
            val dark by vm.dark.collectAsState()
            val accent by vm.accent.collectAsState()
            var active by remember { mutableStateOf(License.isActive(applicationContext)) }
            HomeTheme(dark, accent) {
                if (active) AppNav() else ActivationScreen { active = true }
            }
        }
    }
}
