package com.preferans.scorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.preferans.scorer.ui.PreferansApp
import com.preferans.scorer.ui.theme.PreferansTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PreferansTheme {
                PreferansApp()
            }
        }
    }
}
