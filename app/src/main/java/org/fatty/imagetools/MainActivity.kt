package org.fatty.imagetools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.fatty.imagetools.ui.screens.ImageStitcherScreen
import org.fatty.imagetools.ui.theme.ImageToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageToolsTheme {
                ImageStitcherScreen()
            }
        }
    }
}
