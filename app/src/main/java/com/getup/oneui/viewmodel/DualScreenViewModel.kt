package com.getup.oneui.viewmodel

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.display.DisplayManagerHelper
import com.getup.oneui.display.FoldStateMonitor
import com.getup.oneui.display.WindowAreaHelper
import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.DualScreenConfigRepository
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import com.getup.oneui.preferences.DualScreenPreferences
import com.getup.oneui.service.CoverDisplayService
import com.getup.oneui.shizuku.ShizukuHelper
import com.getup.oneui.ui.presentation.PresentationScreen
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing screen display settings, fold posture tracking,
 * and dual-screen synchronization (via Shizuku elevated State 4 service,
 * WindowAreaController for Samsung foldable rear display, and DisplayManager).
 */
class DualScreenViewModel : ViewModel() {

    private val TAG = "DualScreenViewModel"

    // Backed by the shared DualScreenConfigRepository
    val uiState: StateFlow<SecondaryDisplayConfig> = DualScreenConfigRepository.config

    private val _connectedDisplays = MutableStateFlow<List<DisplayInfo>>(emptyList())
    val connectedDisplays: StateFlow<List<DisplayInfo>> = _connectedDisplays.asStateFlow()

    private val _activeDisplayId = MutableStateFlow<Int?>(null)
    val activeDisplayId: StateFlow<Int?> = _activeDisplayId.asStateFlow()

    private val _isPresentationShowing = MutableStateFlow(false)
    val isPresentationShowing: StateFlow<Boolean> = _isPresentationShowing.asStateFlow()

    val isSecondaryDisplayConnected: StateFlow<Boolean> = _connectedDisplays
        .map { displays -> displays.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val windowAreaHelper = WindowAreaHelper(viewModelScope)
    val foldStateMonitor = FoldStateMonitor(viewModelScope)

    val isRearDisplayAvailable: StateFlow<Boolean> = windowAreaHelper.isRearDisplayAvailable
    val isRearDisplayActive: StateFlow<Boolean> = windowAreaHelper.isRearDisplayActive
    val isDeviceUnfolded: StateFlow<Boolean> = foldStateMonitor.isDeviceUnfolded

    // Shizuku StateFlows
    val isShizukuInstalled: StateFlow<Boolean> = ShizukuHelper.isInstalled
    val isShizukuRunning: StateFlow<Boolean> = ShizukuHelper.isRunning
    val isShizukuAuthorized: StateFlow<Boolean> = ShizukuHelper.isAuthorized
    val isCoverServiceRunning: StateFlow<Boolean> = CoverDisplayService.isServiceRunning

    private var preferences: DualScreenPreferences? = null
    private val _autoStartOnUnfold = MutableStateFlow(true)
    val autoStartOnUnfold: StateFlow<Boolean> = _autoStartOnUnfold.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    // Combined active status across all dual-screen mechanisms
    val isRearScreenActive: StateFlow<Boolean> = combine(
        isRearDisplayActive,
        _isPresentationShowing,
        CoverDisplayService.isServiceRunning
    ) { rearActive, presShowing, serviceRunning ->
        rearActive || presShowing || serviceRunning
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    private var displayManagerHelper: DisplayManagerHelper? = null
    private var activityRef: WeakReference<ComponentActivity>? = null
    private var isAutoStartObserving = false
    private var userManuallyStopped = false
    private var wasUnfolded = false

    private data class AutoStartCheck(
        val isUnfolded: Boolean,
        val autoStart: Boolean,
        val isShizukuAuth: Boolean,
        val isServiceRunning: Boolean,
        val isRearAvailable: Boolean,
        val isRearActive: Boolean
    )

    /**
     * Start monitoring display events, fold posture, and Shizuku status.
     */
    fun startMonitoring(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
        if (preferences == null) {
            preferences = DualScreenPreferences(activity.applicationContext)
            _autoStartOnUnfold.value = preferences?.autoStartOnUnfold ?: true
            _keepScreenOn.value = preferences?.keepScreenOn ?: true
        }

        ShizukuHelper.init(activity.applicationContext)
        startDisplayMonitoring(activity)
        foldStateMonitor.startMonitoring(activity)
        startAutoStartObservation()
    }

    /**
     * Start monitoring display events using DisplayManagerHelper.
     */
    fun startDisplayMonitoring(context: Context) {
        if (displayManagerHelper == null) {
            displayManagerHelper = DisplayManagerHelper(context.applicationContext).apply {
                startListening()
            }
        }
        val helper = displayManagerHelper ?: return
        observeHelper(helper)
    }

    private fun startAutoStartObservation() {
        if (isAutoStartObserving) return
        isAutoStartObserving = true

        viewModelScope.launch {
            combine(
                foldStateMonitor.isDeviceUnfolded,
                _autoStartOnUnfold,
                ShizukuHelper.isAuthorized,
                CoverDisplayService.isServiceRunning,
                windowAreaHelper.isRearDisplayActive
            ) { isUnfolded, autoStart, isShizukuAuth, serviceRunning, isRearActive ->
                AutoStartCheck(
                    isUnfolded = isUnfolded,
                    autoStart = autoStart,
                    isShizukuAuth = isShizukuAuth,
                    isServiceRunning = serviceRunning,
                    isRearAvailable = windowAreaHelper.isRearDisplayAvailable.value,
                    isRearActive = isRearActive
                )
            }.collect { check ->
                if (check.isUnfolded) {
                    if (!wasUnfolded) {
                        userManuallyStopped = false
                    }
                    wasUnfolded = true

                    if (check.autoStart && !userManuallyStopped) {
                        if (check.isShizukuAuth && !check.isServiceRunning) {
                            Log.i(TAG, "Unfold auto-start triggered via Shizuku CoverDisplayService")
                            activityRef?.get()?.let { activity ->
                                CoverDisplayService.start(activity)
                            }
                        } else if (!check.isRearActive && check.isRearAvailable) {
                            Log.i(TAG, "Unfold auto-start triggered for Samsung rear display (WindowArea)")
                            activityRef?.get()?.let { activity ->
                                startRearDisplay(activity)
                            }
                        }
                    }
                } else {
                    wasUnfolded = false
                    userManuallyStopped = false
                    activityRef?.get()?.let { activity ->
                        if (check.isServiceRunning) {
                            Log.i(TAG, "Device folded. Stopping CoverDisplayService.")
                            CoverDisplayService.stop(activity)
                        }
                        if (check.isRearActive) {
                            Log.i(TAG, "Device folded. Stopping rear display.")
                            stopRearDisplay()
                        }
                    }
                }
            }
        }
    }

    private fun observeHelper(helper: DisplayManagerHelper) {
        viewModelScope.launch {
            helper.connectedDisplays.collect { displays ->
                _connectedDisplays.value = displays
            }
        }
        viewModelScope.launch {
            helper.activePresentationDisplayId.collect { id ->
                _activeDisplayId.value = id
            }
        }
        viewModelScope.launch {
            helper.isPresentationShowing.collect { showing ->
                _isPresentationShowing.value = showing
            }
        }
    }

    /**
     * Stop monitoring secondary displays and fold posture.
     */
    fun stopDisplayMonitoring() {
        displayManagerHelper?.stopListening()
        displayManagerHelper = null
    }

    fun stopMonitoring() {
        stopDisplayMonitoring()
        foldStateMonitor.stopMonitoring()
        stopRearDisplay()
        activityRef = null
    }

    /**
     * Toggle the rear/secondary screen presentation.
     * When Shizuku is authorized, starts/stops CoverDisplayService (true background concurrency).
     * Otherwise, falls back to WindowArea rear display or Presentation.
     */
    fun toggleRearDisplay(activity: ComponentActivity, enabled: Boolean) {
        DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = enabled) }
        if (enabled) {
            userManuallyStopped = false
            if (ShizukuHelper.isAuthorized.value) {
                CoverDisplayService.start(activity)
            } else if (isRearDisplayAvailable.value) {
                startRearDisplay(activity)
            } else if (_connectedDisplays.value.isNotEmpty()) {
                syncPresentation(activity)
            }
        } else {
            userManuallyStopped = true
            if (CoverDisplayService.isServiceRunning.value) {
                CoverDisplayService.stop(activity)
            }
            stopRearDisplay()
            syncPresentation(activity)
        }
    }

    /**
     * Explicit toggle for the Shizuku Background Service mode.
     */
    fun toggleShizukuService(context: Context, enabled: Boolean) {
        if (enabled) {
            userManuallyStopped = false
            CoverDisplayService.start(context)
        } else {
            userManuallyStopped = true
            CoverDisplayService.stop(context)
        }
    }

    /**
     * Request Shizuku authorization.
     */
    fun requestShizukuPermission() {
        ShizukuHelper.requestPermission()
    }

    /**
     * Start presenting on Samsung foldable rear display via WindowAreaController.
     */
    fun startRearDisplay(activity: ComponentActivity) {
        DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = true) }
        windowAreaHelper.startRearDisplay(activity, uiState)
    }

    /**
     * Stop presenting on Samsung foldable rear display.
     */
    fun stopRearDisplay() {
        windowAreaHelper.stopRearDisplay()
    }

    /**
     * Update preference for auto-starting rear display when unfolded.
     */
    fun updateAutoStartOnUnfold(enabled: Boolean) {
        _autoStartOnUnfold.value = enabled
        preferences?.autoStartOnUnfold = enabled

        if (enabled && foldStateMonitor.isDeviceUnfolded.value) {
            userManuallyStopped = false
            activityRef?.get()?.let { activity ->
                if (ShizukuHelper.isAuthorized.value && !CoverDisplayService.isServiceRunning.value) {
                    CoverDisplayService.start(activity)
                } else if (isRearDisplayAvailable.value && !windowAreaHelper.isRearDisplayActive.value) {
                    startRearDisplay(activity)
                }
            }
        }
    }

    @VisibleForTesting
    fun setAutoStartOnUnfoldForTest(enabled: Boolean) {
        _autoStartOnUnfold.value = enabled
    }

    /**
     * Update preference for keeping screen on when rear display is active.
     */
    fun updateKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        preferences?.keepScreenOn = enabled
    }

    @VisibleForTesting
    fun setKeepScreenOnForTest(enabled: Boolean) {
        _keepScreenOn.value = enabled
    }

    /**
     * Sync presentation window state based on current settings and available displays (DisplayManager).
     */
    fun syncPresentation(
        context: Context,
        content: @Composable (SecondaryDisplayConfig) -> Unit = { config -> PresentationScreen(config = config) }
    ) {
        if (CoverDisplayService.isServiceRunning.value) {
            displayManagerHelper?.dismissPresentation()
            return
        }

        val helper = displayManagerHelper ?: DisplayManagerHelper(context.applicationContext).also {
            it.startListening()
            displayManagerHelper = it
            observeHelper(it)
        }

        val config = uiState.value
        if (config.isPresentationEnabled && _connectedDisplays.value.isNotEmpty()) {
            helper.showPresentation(
                targetDisplayId = config.targetDisplayId,
                configFlow = uiState,
                content = content
            )
        } else {
            helper.dismissPresentation()
        }
    }

    // --- State update actions for real-time dual-screen synchronization ---

    fun updateClockStyle(clockStyle: ClockStyle) {
        DualScreenConfigRepository.updateConfig { it.copy(clockStyle = clockStyle) }
    }

    fun updateWallpaper(wallpaper: WallpaperOption) {
        DualScreenConfigRepository.updateConfig { it.copy(wallpaper = wallpaper) }
    }

    fun updateSloganText(text: String) {
        DualScreenConfigRepository.updateConfig {
            it.copy(sloganConfig = it.sloganConfig.copy(text = text))
        }
    }

    fun updateSloganConfig(sloganConfig: SloganConfig) {
        DualScreenConfigRepository.updateConfig { it.copy(sloganConfig = sloganConfig) }
    }

    fun updateBatteryOption(batteryOption: BatteryDisplayOption) {
        DualScreenConfigRepository.updateConfig { it.copy(batteryOption = batteryOption) }
    }

    fun togglePresentation(enabled: Boolean) {
        DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = enabled) }
    }

    fun selectTargetDisplay(displayId: Int?) {
        DualScreenConfigRepository.updateConfig { it.copy(targetDisplayId = displayId) }
    }

    fun update24HourFormat(is24Hour: Boolean) {
        DualScreenConfigRepository.updateConfig { it.copy(is24HourFormat = is24Hour) }
    }

    fun updateShowSeconds(showSeconds: Boolean) {
        DualScreenConfigRepository.updateConfig { it.copy(showSeconds = showSeconds) }
    }

    fun updateBrightness(brightness: Float) {
        DualScreenConfigRepository.updateConfig { it.copy(brightnessLevel = brightness.coerceIn(0.1f, 1.0f)) }
    }

    fun resetToDefaults() {
        DualScreenConfigRepository.setConfig(SecondaryDisplayConfig())
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        windowAreaHelper.destroy()
        foldStateMonitor.destroy()
    }
}
