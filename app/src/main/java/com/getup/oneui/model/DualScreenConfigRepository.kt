package com.getup.oneui.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared repository holding the current [SecondaryDisplayConfig].
 * Synchronizes real-time configuration updates between DualScreenViewModel,
 * MainActivity, and CoverDisplayService.
 */
object DualScreenConfigRepository {
    private val _config = MutableStateFlow(SecondaryDisplayConfig())
    val config: StateFlow<SecondaryDisplayConfig> = _config.asStateFlow()

    fun updateConfig(transform: (SecondaryDisplayConfig) -> SecondaryDisplayConfig) {
        _config.update(transform)
    }

    fun setConfig(newConfig: SecondaryDisplayConfig) {
        _config.value = newConfig
    }
}
