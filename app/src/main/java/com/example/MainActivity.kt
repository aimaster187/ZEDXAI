package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.titan.zedxai.firebase.configureAppCheck
import com.titan.zedxai.ui.ZedxRootApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must execute immediately after super.onCreate before any Firebase/AI/UI setup
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            ZedxRootApp()
        }
    }
}
