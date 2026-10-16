package com.getup.oneui.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences storage for dual screen preferences, such as auto-starting
 * the rear display when the foldable device is unfolded, and keeping the screen
 * on to prevent system lock screen timeouts while rear display is active.
 */
class DualScreenPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoStartOnUnfold: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_ON_UNFOLD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START_ON_UNFOLD, value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    companion object {
        const val PREFS_NAME = "dual_screen_prefs"
        const val KEY_AUTO_START_ON_UNFOLD = "pref_auto_start_on_unfold"
        const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
    }
}
