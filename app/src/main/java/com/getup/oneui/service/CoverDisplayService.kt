package com.getup.oneui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.getup.oneui.MainActivity
import com.getup.oneui.R
import com.getup.oneui.display.CoverDisplayOverlayController
import com.getup.oneui.display.SecondaryDisplayPresentation
import com.getup.oneui.model.DualScreenConfigRepository
import com.getup.oneui.preferences.DualScreenPreferences
import com.getup.oneui.shizuku.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service that maintains the dual-screen Presentation on Display 1 (Cover Display)
 * even when MainActivity goes to background, screen turns off, or user switches to other apps.
 */
class CoverDisplayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var displayManager: DisplayManager? = null
    private var activeOverlay: CoverDisplayOverlayController? = null
    private var activePresentation: SecondaryDisplayPresentation? = null
    private var activeDisplayId: Int? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: $displayId")
            checkAndShowPresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            if (activeDisplayId == displayId) {
                dismissPresentation()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Display changed: $displayId")
            if (activePresentation == null) {
                checkAndShowPresentation()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CoverDisplayService onCreate")
        ShizukuHelper.init(applicationContext)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "CoverDisplayService onStartCommand: action=$action")

        when (action) {
            ACTION_STOP -> {
                stopCoverDisplay()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (activePresentation != null) {
                    stopCoverDisplay()
                    return START_NOT_STICKY
                } else {
                    startForegroundWithNotification()
                    enableDualScreenAndPresent()
                }
            }
            ACTION_START -> {
                startForegroundWithNotification()
                enableDualScreenAndPresent()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CoverDisplayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("双屏并发 · 外屏常驻运行中")
            .setContentText("外屏正在常驻显示，内屏熄灭或切换应用不影响外屏")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭外屏", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun enableDualScreenAndPresent() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Requesting State 4 via Shizuku...")
                ShizukuHelper.setConcurrentDisplayState(true, packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable State 4", e)
            }
            checkAndShowPresentation()
        }
    }

    private fun checkAndShowPresentation() {
        val dm = displayManager ?: return
        val secondaryDisplays = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondaryDisplays.isEmpty()) {
            Log.d(TAG, "No secondary displays yet found in DisplayManager")
            return
        }

        val targetDisplay = secondaryDisplays.first()
        val freshDisplay = dm.getDisplay(targetDisplay.displayId)
        if (freshDisplay == null || !freshDisplay.isValid) {
            Log.w(TAG, "Target display ${targetDisplay.displayId} is not valid yet.")
            return
        }

        if (activeOverlay?.isShowing == true && activeDisplayId == freshDisplay.displayId) {
            Log.d(TAG, "Overlay already active on display ${freshDisplay.displayId}")
            return
        }

        dismissPresentation()

        try {
            Log.i(TAG, "Showing CoverDisplayOverlayController on display ${freshDisplay.displayId} (${freshDisplay.name})")
            val overlay = CoverDisplayOverlayController(
                serviceContext = this@CoverDisplayService,
                display = freshDisplay,
                configFlow = DualScreenConfigRepository.config
            )
            val shown = overlay.show()
            if (shown) {
                activeOverlay = overlay
                activeDisplayId = freshDisplay.displayId
            } else {
                Log.w(TAG, "Overlay show returned false, trying fallback SecondaryDisplayPresentation...")
                val presentation = SecondaryDisplayPresentation(
                    outerContext = this@CoverDisplayService,
                    display = freshDisplay,
                    configFlow = DualScreenConfigRepository.config
                )
                presentation.show()
                activePresentation = presentation
                activeDisplayId = freshDisplay.displayId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show on cover display", e)
        }
    }

    private fun dismissPresentation() {
        try {
            activeOverlay?.dismiss()
            activePresentation?.let {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing presentation/overlay", e)
        } finally {
            activeOverlay = null
            activePresentation = null
            activeDisplayId = null
        }
    }

    private fun stopCoverDisplay() {
        dismissPresentation()
        serviceScope.launch {
            try {
                Log.d(TAG, "Resetting device state via Shizuku...")
                ShizukuHelper.setConcurrentDisplayState(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset device state via Shizuku", e)
            } finally {
                _isServiceRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "外屏常驻服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持外屏持续显示并在前台常驻运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CoverDisplayService onDestroy")
        dismissPresentation()
        displayManager?.unregisterDisplayListener(displayListener)
        _isServiceRunning.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CoverDisplayService"
        private const val CHANNEL_ID = "cover_display_channel"
        private const val NOTIFICATION_ID = 2024

        const val ACTION_START = "com.getup.oneui.action.START_COVER_DISPLAY"
        const val ACTION_STOP = "com.getup.oneui.action.STOP_COVER_DISPLAY"
        const val ACTION_TOGGLE = "com.getup.oneui.action.TOGGLE_COVER_DISPLAY"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, CoverDisplayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CoverDisplayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, CoverDisplayService::class.java).apply {
                action = ACTION_TOGGLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
