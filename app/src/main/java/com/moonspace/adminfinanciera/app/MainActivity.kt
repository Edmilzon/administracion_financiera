package com.moonspace.adminfinanciera.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moonspace.adminfinanciera.app.navigation.FinanceNavigation
import com.moonspace.adminfinanciera.core.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTheme {
                FinanceNavigation()
            }
        }
    }
}
