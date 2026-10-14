package com.getup.oneui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.oneui.ui.console.MainConsoleScreen
import com.getup.oneui.ui.theme.双屏并发Theme
import com.getup.oneui.viewmodel.DualScreenViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DualScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start observing secondary display connection lifecycles
        viewModel.startDisplayMonitoring(this)

        setContent {
            双屏并发Theme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val connectedDisplays by viewModel.connectedDisplays.collectAsStateWithLifecycle()

                // Reactive dual-screen synchronization
                LaunchedEffect(uiState, connectedDisplays) {
                    viewModel.syncPresentation(this@MainActivity)
                }

                MainConsoleScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopDisplayMonitoring()
    }
}
