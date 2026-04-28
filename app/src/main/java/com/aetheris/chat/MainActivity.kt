package com.aetheris.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.aetheris.chat.data.repository.SettingsRepository
import com.aetheris.chat.ui.navigation.AetherisNavGraph
import com.aetheris.chat.ui.theme.AetherisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Honour the user's persisted dark-mode preference, falling back to
            // the system setting until DataStore emits its first value.
            val systemDark = isSystemInDarkTheme()
            val darkMode by settingsRepository.darkMode.collectAsState(initial = systemDark)

            AetherisTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AetherisNavGraph()
                }
            }
        }
    }
}
