package com.getup.oneui.ui.console

import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
 * status monitoring, live mirror preview rendering, customization controls,
 * and Shizuku elevated background concurrency.
 */
@Composable
fun MainConsoleScreen(
    viewModel: DualScreenViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedDisplays by viewModel.connectedDisplays.collectAsStateWithLifecycle()
    val isRearDisplayAvailable by viewModel.isRearDisplayAvailable.collectAsStateWithLifecycle()
    val isRearScreenActive by viewModel.isRearScreenActive.collectAsStateWithLifecycle()
    val autoStartOnUnfold by viewModel.autoStartOnUnfold.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val isShizukuInstalled by viewModel.isShizukuInstalled.collectAsStateWithLifecycle()
    val isShizukuRunning by viewModel.isShizukuRunning.collectAsStateWithLifecycle()
    val isShizukuAuthorized by viewModel.isShizukuAuthorized.collectAsStateWithLifecycle()
    val isCoverServiceRunning by viewModel.isCoverServiceRunning.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? ComponentActivity

    MainConsoleContent(
        uiState = uiState,
        connectedDisplays = connectedDisplays,
        isRearDisplayAvailable = isRearDisplayAvailable,
        isRearScreenActive = isRearScreenActive,
        autoStartOnUnfold = autoStartOnUnfold,
        keepScreenOn = keepScreenOn,
        isShizukuInstalled = isShizukuInstalled,
        isShizukuRunning = isShizukuRunning,
        isShizukuAuthorized = isShizukuAuthorized,
        isCoverServiceRunning = isCoverServiceRunning,
        onToggleRearDisplay = { enabled ->
            activity?.let { act ->
                viewModel.toggleRearDisplay(act, enabled)
            } ?: run {
                viewModel.togglePresentation(enabled)
                viewModel.syncPresentation(context)
            }
        },
        onToggleAutoStartOnUnfold = { enabled ->
            viewModel.updateAutoStartOnUnfold(enabled)
        },
        onToggleKeepScreenOn = { enabled ->
            viewModel.updateKeepScreenOn(enabled)
        },
        onRequestShizukuPermission = {
            viewModel.requestShizukuPermission()
        },
        onToggleCoverService = { enabled ->
            viewModel.toggleShizukuService(context, enabled)
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
    isRearDisplayAvailable: Boolean,
    isRearScreenActive: Boolean,
    autoStartOnUnfold: Boolean,
    keepScreenOn: Boolean,
    isShizukuInstalled: Boolean,
    isShizukuRunning: Boolean,
    isShizukuAuthorized: Boolean,
    isCoverServiceRunning: Boolean,
    onToggleRearDisplay: (Boolean) -> Unit,
    onToggleAutoStartOnUnfold: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onToggleCoverService: (Boolean) -> Unit,
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
                    // Shizuku Status Badge
                    if (isShizukuAuthorized) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Shizuku 就绪",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Reset Defaults
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
            // 1. Dual Control Status Banner:
            // High-visibility "外屏背屏显示" 主开关, "展开大屏时自动开启" 开关,
            // "背屏开启时保持常亮" 开关, and Shizuku elevated concurrency controls
            item {
                StatusBanner(
                    connectedDisplays = connectedDisplays,
                    isRearDisplayAvailable = isRearDisplayAvailable,
                    isRearScreenActive = isRearScreenActive,
                    autoStartOnUnfold = autoStartOnUnfold,
                    keepScreenOn = keepScreenOn,
                    isShizukuInstalled = isShizukuInstalled,
                    isShizukuRunning = isShizukuRunning,
                    isShizukuAuthorized = isShizukuAuthorized,
                    isCoverServiceRunning = isCoverServiceRunning,
                    selectedDisplayId = uiState.targetDisplayId,
                    onToggleRearDisplay = onToggleRearDisplay,
                    onToggleAutoStartOnUnfold = onToggleAutoStartOnUnfold,
                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onToggleCoverService = onToggleCoverService,
                    onSelectTargetDisplay = onSelectTargetDisplay
                )
            }

            // 2. Live Secondary Display Mirror Preview Card
            item {
                PreviewMirrorCard(
                    config = uiState,
                    isPresentationShowing = isRearScreenActive || isCoverServiceRunning
                )
            }

            // 3. Clock Style Customization Panel
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

            // 4. Wallpaper and Background Customization Panel
            item {
                WallpaperControlPanel(
                    wallpaper = uiState.wallpaper,
                    brightnessLevel = uiState.brightnessLevel,
                    onWallpaperChange = onWallpaperChange,
                    onBrightnessChange = onBrightnessChange
                )
            }

            // 5. Motivational Slogan Customization Panel
            item {
                SloganControlPanel(
                    sloganConfig = uiState.sloganConfig,
                    onSloganConfigChange = onSloganConfigChange
                )
            }

            // 6. Battery Level Display Customization Panel
            item {
                BatteryControlPanel(
                    batteryOption = uiState.batteryOption,
                    onBatteryOptionChange = onBatteryOptionChange
                )
            }

            // Bottom Spacing for navigation bar inset clearance
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun MainConsoleScreenPreview() {
    双屏并发Theme {
        MainConsoleContent(
            uiState = SecondaryDisplayConfig(),
            connectedDisplays = emptyList(),
            isRearDisplayAvailable = true,
            isRearScreenActive = true,
            autoStartOnUnfold = true,
            keepScreenOn = true,
            isShizukuInstalled = true,
            isShizukuRunning = true,
            isShizukuAuthorized = true,
            isCoverServiceRunning = true,
            onToggleRearDisplay = {},
            onToggleAutoStartOnUnfold = {},
            onToggleKeepScreenOn = {},
            onRequestShizukuPermission = {},
            onToggleCoverService = {},
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
