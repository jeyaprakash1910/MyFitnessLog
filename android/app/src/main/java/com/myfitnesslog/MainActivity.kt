package com.myfitnesslog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myfitnesslog.core.ui.navigation.MyFitnessLogNavHost
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity host for the entire application.
 *
 * The app follows a single-Activity / Navigation Compose architecture
 * (see docs/ANDROID_ARCHITECTURE.md). [AndroidEntryPoint] lets Hilt inject
 * dependencies into this Activity and the ViewModels scoped beneath it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFitnessLogTheme {
                MyFitnessLogNavHost()
            }
        }
    }
}
