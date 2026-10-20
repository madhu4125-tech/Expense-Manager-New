package com.madhu.expensemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the blue header sits under the status bar, so keep its icons light
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val vm: ExpenseViewModel = viewModel()
            val dark = vm.darkMode ?: isSystemInDarkTheme()
            ExpenseTheme(dark) {
                HomeScreen(vm, dark)
            }
        }
    }
}
