package com.ibneilyas.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.navigation.AppNav
import com.ibneilyas.home.ui.theme.HomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: HomeViewModel = viewModel()
            val dark by vm.dark.collectAsState()
            val accent by vm.accent.collectAsState()
            HomeTheme(dark, accent) { AppNav() }
        }
    }
}
