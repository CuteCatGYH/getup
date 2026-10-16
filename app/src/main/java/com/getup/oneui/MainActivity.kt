package com.getup.oneui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.oneui.shizuku.ShizukuHelper
import com.getup.oneui.ui.console.MainConsoleScreen
import com.getup.oneui.ui.theme.双屏并发Theme
import com.getup.oneui.viewmodel.DualScreenViewModel

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private val viewModel: DualScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start observing display, fold state, and Shizuku lifecycles
        viewModel.startMonitoring(this)

        setContent {
            双屏并发Theme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val connectedDisplays by viewModel.connectedDisplays.collectAsStateWithLifecycle()
                val isRearScreenActive by viewModel.isRearScreenActive.collectAsStateWithLifecycle()
                val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

                // Reactive dual-screen synchronization
                LaunchedEffect(uiState, connectedDisplays) {
                    viewModel.syncPresentation(this@MainActivity)
                }

                // Keep screen on only if explicitly requested while dual-screen mode is active
                LaunchedEffect(keepScreenOn, isRearScreenActive) {
                    val window = this@MainActivity.window
                    if (keepScreenOn && isRearScreenActive) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Render Main Console UI
                MainConsoleScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuHelper.refreshStatus()
        if (viewModel.keepScreenOn.value && viewModel.isRearScreenActive.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        super.onPause()
        // Do not hold keep screen on when MainActivity is in the background
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.stopMonitoring()
    }
}
