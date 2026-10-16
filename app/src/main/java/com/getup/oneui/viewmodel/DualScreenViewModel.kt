package com.getup.oneui.viewmodel

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getup.oneui.display.DeviceStateMonitor
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.display.DisplayManagerHelper
import com.getup.oneui.display.FoldPosture
import com.getup.oneui.display.FoldStateMonitor
import com.getup.oneui.display.PostureSettleGate
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

    companion object {
        /**
         * How long the unfolded state must persist before it is trusted as a genuine
         * unfold. DeviceStateManager emits TENT/HALF_OPENED/OPENED transitions in rapid
         * succession during the physical hinge movement; settling avoids acting on the
         * transient intermediates (which caused spurious auto-starts and false manual-stop
         * flags during folding).
         */
        private const val UNFOLD_SETTLE_MS = 600L

        /**
         * How long a half-open / tent posture must persist before the UI trusts it as a
         * *resting* posture (rather than a momentary hinge pass-through). Longer than
         * [UNFOLD_SETTLE_MS] because a physical hinge movement spends <~500ms in the
         * intermediate state. Kept in sync with CoverDisplayService.HALF_OPEN_SETTLE_MS.
         */
        private const val HALF_OPEN_SETTLE_MS = 900L
    }

    private val _connectedDisplays = MutableStateFlow<List<DisplayInfo>>(emptyList())
    val connectedDisplays: StateFlow<List<DisplayInfo>> = _connectedDisplays.asStateFlow()

    private val _isPresentationShowing = MutableStateFlow(false)
    val isPresentationShowing: StateFlow<Boolean> = _isPresentationShowing.asStateFlow()

    val windowAreaHelper = WindowAreaHelper(viewModelScope)
    val foldStateMonitor = FoldStateMonitor(viewModelScope)
    private var deviceStateMonitor: DeviceStateMonitor? = null

    val isRearDisplayAvailable: StateFlow<Boolean> = windowAreaHelper.isRearDisplayAvailable
    val isRearDisplayActive: StateFlow<Boolean> = windowAreaHelper.isRearDisplayActive

    /**
     * Authoritative, *settled* fold posture. Prefers the fold monitor's value but falls back
     * to the crash-safe display-based check when the monitor has not yet observed a change.
     */
    val isDeviceUnfolded: StateFlow<Boolean> = foldStateMonitor.isDeviceUnfolded

    private val _settledPosture = MutableStateFlow<FoldPosture?>(null)

    /**
     * The settled posture (see [PostureSettleGate]): steady OPENED, a *resting* half-open
     * posture, or CLOSED. `null` until first resolved. Drives the UI gate and is exposed so
     * the console can label the half-open case distinctly from fully-open.
     */
    val settledPosture: StateFlow<FoldPosture?> = _settledPosture.asStateFlow()

    /**
     * True when the device is resting in a (settled) half-open posture. Lets the console
     * distinguish "半遮（半开）可用" from a fully-unfolded "展开" state.
     */
    val isHalfOpenResting: StateFlow<Boolean> = _settledPosture
        .map { it == FoldPosture.TRANSITIONAL }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    /**
     * Whether the user is allowed to attempt enabling dual-screen presentation.
     *
     * Keyed on the *settled* posture: a steady OPENED posture always qualifies, and a
     * RESTING half-open posture qualifies too once it has settled. A momentary hinge
     * pass-through during a fold/unfold does NOT qualify (see [PostureSettleGate]).
     *
     * Deliberately independent of whether a secondary display currently exists: the cover
     * display does not exist until State 4 is activated, so requiring a display here would
     * wrongly disable the switch after stopping, and would deadlock the initial auto-start.
     */
    val canPresentOnSecondaryDisplay: StateFlow<Boolean> = _settledPosture
        .map { it?.isUsableForCoverDisplay == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

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
    private var lastServiceRunning = false

    // Settled-posture gate: turns the raw DeviceStateManager burst into a single stable
    // verdict that also understands a *resting* half-open posture (see PostureSettleGate).
    private var postureGate: PostureSettleGate? = null
    private var latestRawPosture: FoldPosture? = null

    private data class AutoStartCheck(
        val isUsable: Boolean,
        val autoStart: Boolean,
        val isServiceRunning: Boolean,
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

        val initialUnfolded = DeviceStateMonitor.isDeviceUnfolded(activity)
        foldStateMonitor.setDeviceUnfoldedForTest(initialUnfolded)

        // Settle gate: the VM is only alive while the Activity is, but it still needs to
        // debounce the DSM burst so the UI gate never enables mid-fold.
        if (postureGate == null) {
            postureGate = PostureSettleGate(
                scope = viewModelScope,
                openedSettleMs = UNFOLD_SETTLE_MS,
                transitionalSettleMs = HALF_OPEN_SETTLE_MS,
                postureProvider = { latestRawPosture },
                logTag = TAG
            )
        }

        if (deviceStateMonitor == null) {
            val dsm = DeviceStateMonitor(activity.applicationContext).apply {
                startListening()
            }
            deviceStateMonitor = dsm
            viewModelScope.launch {
                dsm.isUnfolded.collect { unfolded ->
                    // Feed the authoritative DeviceStateManager signal into both the fold
                    // monitor (so geometric heuristics can never override a fold) and the
                    // VM's own unfolded state.
                    foldStateMonitor.setAuthoritativeUnfoldedForTest(unfolded)
                    foldStateMonitor.setDeviceUnfoldedForTest(unfolded)
                }
            }
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

        // 1. Feed the authoritative DSM posture into the settle gate. The gate decides when
        //    a posture is genuinely stable (steady OPENED, or a *resting* half-open posture).
        viewModelScope.launch {
            deviceStateMonitor?.posture?.collect { raw ->
                latestRawPosture = raw
                postureGate?.onPosture(raw) {
                    val settled = postureGate?.settledPosture
                    Log.i(TAG, "Settled posture changed: $settled")
                    _settledPosture.value = settled
                    onPostureSettled(settled)
                }
            }
        }

        // 2. React to service-lifecycle changes for the manual-stop detection (Samsung [关闭]).
        viewModelScope.launch {
            combine(
                _settledPosture,
                _autoStartOnUnfold,
                CoverDisplayService.isServiceRunning,
                windowAreaHelper.isRearDisplayActive
            ) { posture, autoStart, serviceRunning, isRearActive ->
                AutoStartCheck(
                    isUsable = posture?.isUsableForCoverDisplay == true,
                    autoStart = autoStart,
                    isServiceRunning = serviceRunning,
                    isRearActive = isRearActive
                )
            }.collect { check ->
                if (check.isUsable) {
                    // If the service was running and stopped while the device was in a STABLE
                    // usable posture (and the screen is interactive), it means the user tapped
                    // Samsung's system [关闭] button — mark it as a manual stop.
                    if (lastServiceRunning && !check.isServiceRunning) {
                        Log.i(TAG, "Service stopped while posture stable (e.g. Samsung system [关闭] clicked). Marking userManuallyStopped = true")
                        userManuallyStopped = true
                    }
                    lastServiceRunning = check.isServiceRunning
                } else {
                    lastServiceRunning = check.isServiceRunning
                    // Anti-accidental touch: stop presenting. We do NOT stop the service here
                    // when auto-start is enabled — the service owns its own standby lifecycle
                    // and must survive the fold (the Activity/ViewModel is destroyed on fold).
                    activityRef?.get()?.let { activity ->
                        if (check.isServiceRunning && !_autoStartOnUnfold.value) {
                            Log.i(TAG, "Posture no longer usable while service running; auto-start disabled -> stopping CoverDisplayService.")
                            CoverDisplayService.stop(activity)
                        }
                        if (check.isRearActive) {
                            Log.i(TAG, "Posture no longer usable. Stopping rear display.")
                            stopRearDisplay()
                        }
                    }
                }
            }
        }
    }

    /**
     * React to a freshly settled posture: clear the manual-stop flag and, when auto-start is
     * enabled, start the cover display service. A resting half-open posture triggers this too.
     */
    private fun onPostureSettled(posture: FoldPosture?) {
        if (posture?.isUsableForCoverDisplay != true) return
        userManuallyStopped = false
        if (!_autoStartOnUnfold.value) return
        if (CoverDisplayService.isServiceRunning.value) return
        Log.i(TAG, "Settled usable posture $posture; triggering auto-start")
        attemptAutoStartWithRetry()
    }

    /**
     * Attempts the unfold auto-start, retrying a few times with a short backoff because the
     * cover display frequently is not yet enumerated by DisplayManager at the instant the
     * fold callback fires. Each attempt re-validates the crash-safe preconditions so we never
     * create a window on a folded/single-display configuration.
     */
    private fun attemptAutoStartWithRetry(attempt: Int = 0, maxAttempts: Int = 4) {
        val activity = activityRef?.get()
        if (activity == null) {
            Log.w(TAG, "Auto-start skipped: activity reference is gone")
            return
        }
        if (userManuallyStopped) return
        if (!_autoStartOnUnfold.value) return

        // Stop retrying if the posture is no longer usable (folded again in the meantime).
        if (!canPresentOnSecondaryDisplay.value) {
            Log.i(TAG, "Auto-start aborted: posture no longer usable")
            return
        }

        val isShizukuAuth = ShizukuHelper.isAuthorized.value
        val serviceRunning = CoverDisplayService.isServiceRunning.value
        val rearActive = windowAreaHelper.isRearDisplayActive.value
        val secondaryPresent = DeviceStateMonitor.hasSecondaryDisplay(activity)

        when {
            isShizukuAuth -> {
                if (serviceRunning) return
                // The service itself re-validates and retries internally; starting is safe
                // even before the display is enumerated.
                Log.i(TAG, "Unfold auto-start #$attempt via Shizuku CoverDisplayService")
                DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = true) }
                CoverDisplayService.start(activity)
                return
            }

            secondaryPresent && windowAreaHelper.isRearDisplayAvailable.value -> {
                if (rearActive) return
                Log.i(TAG, "Unfold auto-start #$attempt via Samsung rear display (WindowArea)")
                DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = true) }
                startRearDisplay(activity)
                return
            }

            secondaryPresent && _connectedDisplays.value.isNotEmpty() -> {
                Log.i(TAG, "Unfold auto-start #$attempt via Presentation")
                DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = true) }
                syncPresentation(activity)
                return
            }
        }

        if (attempt < maxAttempts) {
            Log.d(TAG, "Auto-start deferred: secondary display not ready (attempt=$attempt)")
            viewModelScope.launch {
                kotlinx.coroutines.delay(250L * (attempt + 1))
                attemptAutoStartWithRetry(attempt + 1, maxAttempts)
            }
        } else {
            Log.w(TAG, "Auto-start gave up after $maxAttempts attempts: no secondary display available")
        }
    }

    private fun observeHelper(helper: DisplayManagerHelper) {
        viewModelScope.launch {
            helper.connectedDisplays.collect { displays ->
                _connectedDisplays.value = displays
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
        deviceStateMonitor?.stopListening()
        deviceStateMonitor = null
        stopRearDisplay()
        activityRef = null
    }

    /**
     * Toggle the rear/secondary screen presentation.
     * When Shizuku is authorized, starts/stops CoverDisplayService (true background concurrency).
     * Otherwise, falls back to WindowArea rear display or Presentation.
     */
    fun toggleRearDisplay(activity: ComponentActivity, enabled: Boolean) {
        // Anti-mistouch gate: enabling is only allowed while the device is in a *settled*
        // usable posture (steady OPENED, or a resting half-open posture). We intentionally do
        // NOT additionally require a secondary display to already exist, because activating
        // State 4 is what creates the cover display; requiring it here would trap the user
        // (switch could never be re-enabled after stopping, and auto-start would deadlock).
        if (enabled && !canPresentOnSecondaryDisplay.value) {
            Log.w(TAG, "Cannot enable rear display while posture is not usable (anti-accidental touch)")
            return
        }
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
        if (enabled && !canPresentOnSecondaryDisplay.value) {
            Log.w(TAG, "Cannot start Shizuku service while posture is not usable (anti-accidental touch)")
            return
        }
        if (enabled) {
            userManuallyStopped = false
            DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = true) }
            CoverDisplayService.start(context)
        } else {
            userManuallyStopped = true
            DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = false) }
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
        activityRef?.get()?.let { activity ->
            CoverDisplayService.setAutoStartOnUnfold(activity, enabled)
        }

        if (enabled && canPresentOnSecondaryDisplay.value) {
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
        activityRef?.get()?.let { activity ->
            CoverDisplayService.setKeepScreenOn(activity, enabled)
        }
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
