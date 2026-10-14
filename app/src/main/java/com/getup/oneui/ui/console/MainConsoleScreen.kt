package com.getup.oneui.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import com.getup.oneui.ui.theme.双屏并发Theme
import com.getup.oneui.viewmodel.DualScreenViewModel
import kotlinx.coroutines.launch

/**
 * Main Console Screen managing secondary display presentation settings,
 * status monitoring, live mirror preview rendering, and customization controls.
 */
@Composable
fun MainConsoleScreen(
    viewModel: DualScreenViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedDisplays by viewModel.connectedDisplays.collectAsStateWithLifecycle()
    val isPresentationShowing by viewModel.isPresentationShowing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MainConsoleContent(
        uiState = uiState,
        connectedDisplays = connectedDisplays,
        isPresentationShowing = isPresentationShowing,
        onTogglePresentation = { enabled ->
            viewModel.togglePresentation(enabled)
            viewModel.syncPresentation(context)
        },
        onSelectTargetDisplay = { id ->
            viewModel.selectTargetDisplay(id)
            viewModel.syncPresentation(context)
        },
        onClockStyleChange = { style ->
            viewModel.updateClockStyle(style)
            viewModel.syncPresentation(context)
        },
        on24HourFormatChange = { is24 ->
            viewModel.update24HourFormat(is24)
            viewModel.syncPresentation(context)
        },
        onShowSecondsChange = { showSec ->
            viewModel.updateShowSeconds(showSec)
            viewModel.syncPresentation(context)
        },
        onSloganConfigChange = { config ->
            viewModel.updateSloganConfig(config)
            viewModel.syncPresentation(context)
        },
        onWallpaperChange = { option ->
            viewModel.updateWallpaper(option)
            viewModel.syncPresentation(context)
        },
        onBrightnessChange = { level ->
            viewModel.updateBrightness(level)
            viewModel.syncPresentation(context)
        },
        onBatteryOptionChange = { option ->
            viewModel.updateBatteryOption(option)
            viewModel.syncPresentation(context)
        },
        onResetDefaults = {
            viewModel.resetToDefaults()
            viewModel.syncPresentation(context)
        },
        modifier = modifier
    )
}

/**
 * Stateless content composable for Main Console UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainConsoleContent(
    uiState: SecondaryDisplayConfig,
    connectedDisplays: List<DisplayInfo>,
    isPresentationShowing: Boolean,
    onTogglePresentation: (Boolean) -> Unit,
    onSelectTargetDisplay: (Int?) -> Unit,
    onClockStyleChange: (ClockStyle) -> Unit,
    on24HourFormatChange: (Boolean) -> Unit,
    onShowSecondsChange: (Boolean) -> Unit,
    onSloganConfigChange: (SloganConfig) -> Unit,
    onWallpaperChange: (WallpaperOption) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBatteryOptionChange: (BatteryDisplayOption) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dual Screen Console",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onResetDefaults()
                            scope.launch {
                                snackbarHostState.showSnackbar("Settings reset to defaults")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Secondary Screen Status Banner with activation toggle
            item {
                StatusBanner(
                    connectedDisplays = connectedDisplays,
                    isPresentationEnabled = uiState.isPresentationEnabled,
                    isPresentationShowing = isPresentationShowing,
                    selectedDisplayId = uiState.targetDisplayId,
                    onTogglePresentation = onTogglePresentation,
                    onSelectTargetDisplay = onSelectTargetDisplay
                )
            }

            // 2. Live Secondary Display Mirror Preview Card
            item {
                PreviewMirrorCard(
                    config = uiState,
                    isPresentationShowing = isPresentationShowing
                )
            }

            // Section Header
            item {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Display Style Customization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize the visual layout rendered on the secondary/cover display in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Control Panels
            item {
                ClockControlPanel(
                    clockStyle = uiState.clockStyle,
                    is24HourFormat = uiState.is24HourFormat,
                    showSeconds = uiState.showSeconds,
                    onClockStyleChange = onClockStyleChange,
                    on24HourFormatChange = on24HourFormatChange,
                    onShowSecondsChange = onShowSecondsChange
                )
            }

            item {
                SloganControlPanel(
                    sloganConfig = uiState.sloganConfig,
                    onSloganConfigChange = onSloganConfigChange
                )
            }

            item {
                WallpaperControlPanel(
                    wallpaper = uiState.wallpaper,
                    brightnessLevel = uiState.brightnessLevel,
                    onWallpaperChange = onWallpaperChange,
                    onBrightnessChange = onBrightnessChange
                )
            }

            item {
                BatteryControlPanel(
                    batteryOption = uiState.batteryOption,
                    onBatteryOptionChange = onBatteryOptionChange
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun MainConsoleContentPreview() {
    双屏并发Theme {
        MainConsoleContent(
            uiState = SecondaryDisplayConfig(),
            connectedDisplays = listOf(
                DisplayInfo(id = 1, name = "Cover Screen", width = 1080, height = 1080, refreshRate = 120f, isPresentationDisplay = true)
            ),
            isPresentationShowing = true,
            onTogglePresentation = {},
            onSelectTargetDisplay = {},
            onClockStyleChange = {},
            on24HourFormatChange = {},
            onShowSecondsChange = {},
            onSloganConfigChange = {},
            onWallpaperChange = {},
            onBrightnessChange = {},
            onBatteryOptionChange = {},
            onResetDefaults = {}
        )
    }
}
