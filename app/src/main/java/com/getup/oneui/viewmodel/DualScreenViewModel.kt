package com.getup.oneui.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.display.DisplayManagerHelper
import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import com.getup.oneui.ui.presentation.PresentationScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing screen display settings and reactive StateFlow synchronization
 * for dual-screen operation between main console and secondary presentation display.
 */
class DualScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SecondaryDisplayConfig())
    val uiState: StateFlow<SecondaryDisplayConfig> = _uiState.asStateFlow()

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

    private var displayManagerHelper: DisplayManagerHelper? = null

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
     * Stop monitoring secondary displays.
     */
    fun stopDisplayMonitoring() {
        displayManagerHelper?.stopListening()
        displayManagerHelper = null
    }

    /**
     * Sync presentation window state based on current settings and available displays.
     */
    fun syncPresentation(
        context: Context,
        content: @Composable (SecondaryDisplayConfig) -> Unit = { config -> PresentationScreen(config = config) }
    ) {
        val helper = displayManagerHelper ?: DisplayManagerHelper(context.applicationContext).also {
            it.startListening()
            displayManagerHelper = it
            observeHelper(it)
        }

        val config = _uiState.value
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
        _uiState.update { it.copy(clockStyle = clockStyle) }
    }

    fun updateWallpaper(wallpaper: WallpaperOption) {
        _uiState.update { it.copy(wallpaper = wallpaper) }
    }

    fun updateSloganText(text: String) {
        _uiState.update {
            it.copy(sloganConfig = it.sloganConfig.copy(text = text))
        }
    }

    fun updateSloganConfig(sloganConfig: SloganConfig) {
        _uiState.update { it.copy(sloganConfig = sloganConfig) }
    }

    fun updateBatteryOption(batteryOption: BatteryDisplayOption) {
        _uiState.update { it.copy(batteryOption = batteryOption) }
    }

    fun togglePresentation(enabled: Boolean) {
        _uiState.update { it.copy(isPresentationEnabled = enabled) }
    }

    fun selectTargetDisplay(displayId: Int?) {
        _uiState.update { it.copy(targetDisplayId = displayId) }
    }

    fun update24HourFormat(is24Hour: Boolean) {
        _uiState.update { it.copy(is24HourFormat = is24Hour) }
    }

    fun updateShowSeconds(showSeconds: Boolean) {
        _uiState.update { it.copy(showSeconds = showSeconds) }
    }

    fun updateBrightness(brightness: Float) {
        _uiState.update { it.copy(brightnessLevel = brightness.coerceIn(0.1f, 1.0f)) }
    }

    fun resetToDefaults() {
        _uiState.value = SecondaryDisplayConfig()
    }

    override fun onCleared() {
        super.onCleared()
        stopDisplayMonitoring()
    }
}
