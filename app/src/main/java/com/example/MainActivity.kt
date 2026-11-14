package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.GolosGidApp
import com.example.ui.theme.GolosGidTheme
import com.example.voice.VoiceEngineStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GolosGidTheme {
                GolosGidApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VoiceEngineStateHolder.setAppInForeground(true)
    }

    override fun onPause() {
        VoiceEngineStateHolder.setAppInForeground(false)
        super.onPause()
    }
}
