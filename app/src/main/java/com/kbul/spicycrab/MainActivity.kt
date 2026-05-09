package com.kbul.spicycrab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kbul.spicycrab.ui.nav.AppNav
import com.kbul.spicycrab.ui.theme.SpicyCrabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpicyCrabTheme {
                AppNav()
            }
        }
    }
}
