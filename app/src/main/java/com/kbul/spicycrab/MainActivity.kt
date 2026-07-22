package com.kbul.spicycrab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.ui.nav.AppNav
import com.kbul.spicycrab.ui.theme.SpicyCrabTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthConnect: HealthConnectRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpicyCrabTheme {
                AppNav()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { healthConnect.sync() }
    }
}
