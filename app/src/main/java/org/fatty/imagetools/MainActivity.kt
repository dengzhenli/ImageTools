package org.fatty.imagetools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.fatty.imagetools.ui.screens.ImageStitcherScreen
import org.fatty.imagetools.ui.theme.ImageToolsTheme
import org.fatty.imagetools.utils.ALog

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageToolsTheme {
                ImageStitcherScreen()
            }
        }
        ALog.d(TAG, "onCreate")
    }
}
