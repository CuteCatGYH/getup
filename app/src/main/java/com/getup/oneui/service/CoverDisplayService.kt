package com.getup.oneui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.getup.oneui.MainActivity
import com.getup.oneui.R
import com.getup.oneui.display.CoverDisplayOverlayController
import com.getup.oneui.display.DeviceStateMonitor
import com.getup.oneui.display.FoldPosture
import com.getup.oneui.display.PostureSettleGate
import com.getup.oneui.display.SecondaryDisplayPresentation
import com.getup.oneui.model.DualScreenConfigRepository
import com.getup.oneui.preferences.DualScreenPreferences
import com.getup.oneui.shizuku.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service that manages the dual-screen presentation on the cover display (Display 1).
 * Features:
 * 1. Independent background fold state and posture monitoring via DeviceStateManager and DisplayManager.
 * 2. Anti-accidental touch (防误触): If device is folded, immediately cancels State 4 and stops.
 * 3. Screen unlock (ACTION_USER_PRESENT) and screen on (ACTION_SCREEN_ON) re-triggering.
 * 4. Screen off (ACTION_SCREEN_OFF) keeps service alive in background without killing auto-start.
 * 5. Accurate detection of Samsung native system [关闭] notification to prevent state mutex/desync.
 * 6. Low-priority notification without duplicate dismiss buttons.
 * 7. Respects keepScreenOn setting so auto screen timeout works normally.
 */
class CoverDisplayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var displayManager: DisplayManager? = null
    private var activeOverlay: CoverDisplayOverlayController? = null
    private var activePresentation: SecondaryDisplayPresentation? = null
    private var activeDisplayId: Int? = null

    private lateinit var preferences: DualScreenPreferences
    private lateinit var deviceStateMonitor: DeviceStateMonitor

    private var userManuallyStopped = false
    private var isScreenReceiverRegistered = false
    private var isStopping = false

    // When true, the service deliberately stays alive across a folded period so it can
    // auto-present on the next unfold (the Activity/ViewModel is destroyed on fold).
    private var isPersistentStandby = false

    // Debounce gate that turns the raw DeviceStateManager burst into a single settled
    // posture verdict. It distinguishes:
    //   - a transient OPENED blip mid-fold  -> must NOT light the cover display (缺陷 A), and
    //   - a transient HALF_OPENED/TENT pass-through -> must NOT count as a resting posture,
    //   - a genuinely resting half-open posture (stable for a longer window) -> usable.
    // The ViewModel has its own gate, but it is destroyed on fold, so the service owns this one.
    private lateinit var postureGate: PostureSettleGate

    // Debounce job for the wake/unlock re-presentation path, so a burst of SCREEN_ON +
    // USER_PRESENT broadcasts results in a single present attempt.
    private var presentationRecheckJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Screen broadcast received: action=$action")

            when (action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    // Use the authoritative DeviceStateManager posture first (Direction 4.1);
                    // fall back to the geometric check only when the DSM has no state yet.
                    val posture = authoritativePostureOrNull()
                    val usable = posture?.isUsableForCoverDisplay
                        ?: (deviceStateMonitor.isUnfolded.value || DeviceStateMonitor.isDeviceUnfolded(applicationContext))

                    Log.d(TAG, "Device wake/unlock: posture=$posture usable=$usable, autoStart=${preferences.autoStartOnUnfold}, manualStop=$userManuallyStopped")

                    if (usable && !userManuallyStopped) {
                        // Debounce the posture, then FORCE a present. We must use the forced
                        // recheck here: after a screen-off the overlay was dismissed, but the
                        // device may have stayed unfolded the whole time, so the settled
                        // posture is UNCHANGED (still OPENED). A change-only check would
                        // suppress the callback and the back screen would stay dark until the
                        // user folds/unfolds again.
                        presentationRecheckJob?.cancel()
                        presentationRecheckJob = serviceScope.launch {
                            delay(PRESENT_RECHECK_DEBOUNCE_MS)
                            if (isStopping || userManuallyStopped) return@launch
                            postureGate.recheck(authoritativePostureOrNull()) {
                                if (isStopping || userManuallyStopped) return@recheck
                                if (!postureGate.isUsable) return@recheck
                                if (activeOverlay?.isShowing == true) return@recheck
                                Log.i(TAG, "Re-presenting cover display after wake/unlock.")
                                enableDualScreenAndPresent()
                            }
                        }
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Device screen off. Dismissing presentation overlay until wake.")
                    dismissPresentation()
                }
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (isStopping) return
            Log.d(TAG, "Display added: $displayId")
            checkAndShowPresentationWithRetry(retries = 2, delayMs = 200L)
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            if (activeDisplayId == displayId) {
                Log.i(TAG, "Cover display $displayId removed. Dismissing overlay.")
                dismissPresentation()

                // The cover display is removed in TWO very different situations:
                //   a) The device was physically FOLDED. Samsung tears down State 4 and the
                //      cover display disappears from DisplayManager. Here we MUST keep the
                //      service alive in persistent standby so it can detect the NEXT unfold
                //      and re-present (the Activity/ViewModel is destroyed on fold).
                //   b) The user tapped Samsung's system [关闭] action while still unfolded.
                //      The device state remains OPENED, so this is a genuine manual stop.
                //
                // We therefore only treat the removal as a manual stop when the
                // authoritative DeviceStateManager state says the device is STILL unfolded.
                // A folded/unknown state must never kill the standby service (缺陷 B).
                if (_isServiceRunning.value && !isStopping) {
                    // Event ordering between the DeviceStateManager CLOSED callback and the
                    // DisplayManager removal is NOT deterministic: on a physical fold the
                    // removal can arrive BEFORE the state callback, so the state still reads
                    // OPENED for a few ms. Deciding immediately would therefore misfire as a
                    // manual stop and kill the standby service (缺陷 B / 缺陷 1).
                    // We defer the decision by a short grace period and re-evaluate the
                    // authoritative state afterwards.
                    val removalDisplayId = displayId
                    serviceScope.launch {
                        delay(DISPLAY_REMOVAL_GRACE_MS)
                        if (isStopping || userManuallyStopped) return@launch
                        // Samsung's system [关闭] action only appears while the device is
                        // fully OPENED. If the posture is not (still) a resting OPENED one
                        // after the grace period, the removal must have been caused by a
                        // fold: keep the service in standby rather than killing it (缺陷 B).
                        if (authoritativePostureOrNull() != FoldPosture.OPENED) {
                            Log.i(TAG, "Cover display $removalDisplayId removed; device is folded/transitioning (state=${deviceStateMonitor.currentState.value}). Keeping service in standby.")
                            if (preferences.autoStartOnUnfold) enterPersistentStandby()
                            return@launch
                        }
                        if (isScreenInteractive()) {
                            Log.i(TAG, "Cover display $removalDisplayId removed while stably OPENED & interactive (Samsung [关闭]); stopping service cleanly.")
                            userManuallyStopped = true
                            stopCoverDisplay()
                        }
                    }
                }
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (isStopping) return
            Log.d(TAG, "Display changed: $displayId")
            // NOTE: We intentionally do NOT stop the service here based on display-dimension
            // heuristics. Those heuristics are unreliable during the fold transition and were
            // killing the service right after it entered persistent standby. Fold/unfold is
            // owned exclusively by the authoritative DeviceStateManager callback
            // (handleDeviceStateChanged).
            if (activePresentation == null && activeOverlay == null && !isPersistentStandby) {
                checkAndShowPresentationWithRetry(retries = 1, delayMs = 200L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CoverDisplayService onCreate")
        preferences = DualScreenPreferences(applicationContext)
        ShizukuHelper.init(applicationContext)

        // Debounce gate for auto-present decisions; it reads the authoritative posture from
        // the DeviceStateMonitor below (assigned before the gate is ever signalled).
        postureGate = PostureSettleGate(
            scope = serviceScope,
            openedSettleMs = UNFOLD_SETTLE_MS,
            transitionalSettleMs = HALF_OPEN_SETTLE_MS,
            postureProvider = { authoritativePostureOrNull() },
            logTag = TAG
        )

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        deviceStateMonitor = DeviceStateMonitor(applicationContext).apply {
            onStateChangedListener = { oldState, newState ->
                handleDeviceStateChanged(oldState, newState)
            }
            startListening()
        }

        registerScreenReceiver()
        _isServiceRunning.value = true
    }

    private fun registerScreenReceiver() {
        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
            isScreenReceiverRegistered = true
            Log.d(TAG, "Registered screen wake and unlock broadcast receiver")
        }
    }

    /**
     * The **authoritative** fold posture, backed ONLY by the DeviceStateManager.
     *
     * This is the single source of truth for all automatic linkage decisions in the service
     * (README 方向 4.1 / 4.3): geometric display-size heuristics are intentionally NOT
     * consulted here, because they are unreliable during the fold transition.
     * Returns null when the DeviceStateManager has not yet reported a state.
     */
    private fun authoritativePostureOrNull(): FoldPosture? =
        FoldPosture.fromRawState(deviceStateMonitor.currentState.value)

    /**
     * Feed the latest raw posture through [postureGate] for debouncing. [onSettled] is
     * invoked (on the main thread) once a posture has been confirmed stable, and only when
     * the stable verdict actually changes.
     */
    private fun scheduleSettledPostureCheck(onSettled: () -> Unit) {
        postureGate.onPosture(authoritativePostureOrNull(), onSettled)
    }

    private fun cancelSettledPostureCheck() {
        postureGate.reset()
    }

    /** Whether the current posture is a settled, usable one (per [postureGate]). */
    private fun isPostureUsableNow(): Boolean =
        postureGate.isUsable || authoritativePostureOrNull()?.isUsableForCoverDisplay == true

    private fun isScreenInteractive(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    private fun handleDeviceStateChanged(oldState: Int?, newState: Int) {
        if (isStopping) return
        Log.i(TAG, "Device state transition: $oldState -> $newState")

        // Feed every transition into the posture gate. The gate itself rejects transient
        // pass-throughs (a HALF_OPENED that does not persist, an OPENED blip mid-fold) and
        // only reports a settled, usable posture once it is stable and re-verified.
        scheduleSettledPostureCheck {
            if (isStopping || userManuallyStopped) return@scheduleSettledPostureCheck
            onSettledPosture(postureGate.settledPosture)
        }

        // 1. When the foldable device is closed (folded):
        // Anti-misoperation: IMMEDIATELY cancel State 4 and dismiss the overlay.
        //
        // IMPORTANT: If auto-start-on-unfold is enabled, we must NOT tear the service down.
        // The MainActivity/ViewModel is destroyed when the phone is folded (it was running on
        // the cover display), so the service is the ONLY component left that can detect the
        // next unfold and re-present. We therefore stay alive in foreground standby, keeping
        // the DeviceStateManager callback registered, and auto-present when the device unfolds
        // (or comes to rest half-open) again. Only when auto-start is disabled do we shut down.
        if (newState == DeviceStateMonitor.STATE_CLOSED) {
            Log.i(TAG, "Device folded (STATE_CLOSED). Canceling State 4 and dismissing overlay.")
            userManuallyStopped = false // Physical fold is NOT a manual cancel of auto-start
            // A fold invalidates any pending/active settled verdict so a stale debounce can
            // never fire a spurious present after the fold.
            cancelSettledPostureCheck()

            // Tear down the presentation itself, but keep the service (and its callbacks) alive.
            dismissPresentation()
            serviceScope.launch {
                try {
                    ShizukuHelper.setConcurrentDisplayState(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reset device state on fold", e)
                }
            }

            if (preferences.autoStartOnUnfold) {
                Log.i(TAG, "Auto-start enabled: entering persistent standby to wait for next unfold/half-open rest.")
                enterPersistentStandby()
            } else {
                Log.i(TAG, "Auto-start disabled: stopping service after fold.")
                stopCoverDisplay()
            }
            return
        }

        val isInteractive = isScreenInteractive()

        // 2. When State 4 is reset by the system:
        //    - 4 -> 3 (OPENED) while interactive is Samsung's system [关闭] action: manual stop.
        //    - 4 -> 2/1 is a FOLD in progress (Samsung tears down concurrency first); this is
        //      NOT a manual stop, so we must not mark it as one.
        if (oldState == DeviceStateMonitor.STATE_CONCURRENT && newState != DeviceStateMonitor.STATE_CONCURRENT) {
            val isManualCloseToOpened = newState == DeviceStateMonitor.STATE_OPENED
            if (isManualCloseToOpened && isInteractive) {
                Log.i(TAG, "Detected concurrent mode cancellation via Samsung notification [关闭] while interactive. Stopping service.")
                userManuallyStopped = true
                stopCoverDisplay()
            } else {
                // Folding (4 -> 2/1) or screen off: dismiss and let the posture gate / CLOSED
                // handler decide the lifecycle. Not a manual stop.
                Log.i(TAG, "State 4 reset (newState=$newState, interactive=$isInteractive). Dismissing overlay and awaiting posture.")
                dismissPresentation()
            }
            return
        }

        // 3. Everything else (OPENED / CONCURRENT / HALF_OPENED / TENT) is handled by the
        //    settled-posture callback scheduled above (onSettledPosture). We deliberately do
        //    NOT act on raw HALF_OPENED/TENT here, because those are bidirectional hinge
        //    intermediates during BOTH folding and unfolding.
    }

    /**
     * React to a freshly settled, authoritative posture. Only called when the settled
     * verdict actually changes.
     */
    private fun onSettledPosture(posture: FoldPosture?) {
        if (isStopping || userManuallyStopped) return
        Log.i(TAG, "Settled posture: $posture")
        when (posture) {
            FoldPosture.OPENED, FoldPosture.TRANSITIONAL -> {
                if (!preferences.autoStartOnUnfold) return
                Log.i(TAG, "Scheduling auto-start on settled $posture.")
                // Enter foreground first (contract), then wait for the cover display.
                startForegroundWithNotification(isStandby = true)
                exitPersistentStandby()
                holdStandbyUntilSecondaryDisplayAvailable()
            }
            FoldPosture.CLOSED, null -> {
                // Nothing to present; the fold path already reset everything.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "CoverDisplayService onStartCommand: action=$action")

        // ----------------------------------------------------------------------------
        // Foreground-service lifecycle contract:
        // When this service is launched via startForegroundService(), the system
        // requires a call to startForeground() within ~5 seconds, otherwise it aborts
        // the process with ForegroundServiceDidNotStartInTimeException.
        // Therefore, for every entry point that may be reached via startForegroundService
        // (START / TOGGLE), we enter the foreground state FIRST with a standby
        // notification, and only afterwards decide whether to present or to transition
        // away. We never call stopSelf() before startForeground().
        // ----------------------------------------------------------------------------
        when (action) {
            ACTION_STOP -> {
                userManuallyStopped = true
                // ACTION_STOP is always fired via startService() (not startForegroundService),
                // so entering the foreground is not required here.
                stopCoverDisplay()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> {
                // Must enter foreground first to satisfy the startForegroundService contract.
                startForegroundWithNotification(isStandby = true)

                // Feed the current posture through the gate so a resting half-open posture can
                // be recognised, while a fold pass-through cannot.
                scheduleSettledPostureCheck {
                    if (isStopping || userManuallyStopped) return@scheduleSettledPostureCheck
                    onSettledPosture(postureGate.settledPosture)
                }
                val usableNow = isPostureUsableNow()
                if (!usableNow) {
                    Log.w(TAG, "TOGGLE refused: posture not usable (state=${deviceStateMonitor.currentState.value}). Entering standby.")
                    // Hold standby (persistent when auto-start is on) so the next unfold/rest can
                    // still engage; do not stopSelf() (avoids the FGS crash).
                    if (preferences.autoStartOnUnfold) enterPersistentStandby() else holdStandbyUntilSecondaryDisplayAvailable()
                    return START_STICKY
                }
                if (activeOverlay != null || activePresentation != null) {
                    userManuallyStopped = true
                    stopCoverDisplay()
                    return START_NOT_STICKY
                } else {
                    userManuallyStopped = false
                    enableDualScreenAndPresent()
                }
            }

            ACTION_UPDATE_KEEP_SCREEN_ON -> {
                val keepOn = intent?.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, preferences.keepScreenOn) ?: preferences.keepScreenOn
                preferences.keepScreenOn = keepOn
                activeOverlay?.updateKeepScreenOn(keepOn)
            }
            ACTION_UPDATE_AUTO_START -> {
                val autoStart = intent?.getBooleanExtra(EXTRA_AUTO_START, preferences.autoStartOnUnfold) ?: preferences.autoStartOnUnfold
                preferences.autoStartOnUnfold = autoStart
            }

            ACTION_START -> {
                // Must enter foreground first to satisfy the startForegroundService contract.
                startForegroundWithNotification(isStandby = true)
                isStopping = false
                userManuallyStopped = false

                // Usable postures are: steady OPENED, or a *resting* half-open posture once
                // it has settled (see PostureSettleGate). A folded device goes to standby.
                scheduleSettledPostureCheck {
                    if (isStopping || userManuallyStopped) return@scheduleSettledPostureCheck
                    onSettledPosture(postureGate.settledPosture)
                }
                val usableNow = isPostureUsableNow()
                if (!usableNow) {
                    // Device is folded: hold standby (persistent when auto-start is enabled) so
                    // the next unfold/half-open rest can auto-present. Never stopSelf() before startForeground.
                    Log.w(TAG, "START while not usable (state=${deviceStateMonitor.currentState.value}): entering standby instead of presenting.")
                    if (preferences.autoStartOnUnfold) enterPersistentStandby() else holdStandbyUntilSecondaryDisplayAvailable()
                    return START_STICKY
                }

                // Usable posture: activate State 4 and present. The cover display may not be
                // enumerated yet; checkAndShowPresentationWithRetry() waits for it.
                enableDualScreenAndPresent()
            }
        }

        return START_STICKY
    }

    /**
     * Keeps the service alive in foreground standby and periodically re-checks whether the
     * cover display has become available, presenting as soon as it is. This is what makes
     * "unfold then immediately present" reliable without ever violating the foreground
     * service start contract.
     */
    private fun holdStandbyUntilSecondaryDisplayAvailable(attempt: Int = 0) {
        if (isStopping) return
        if (userManuallyStopped) return
        if (activeOverlay?.isShowing == true) return

        // Direction 4.3: the standby loop must NOT rely on geometric display-size heuristics,
        // which blip to "unfolded" for ~30ms during a physical fold (缺陷 A). We only trust
        // the AUTHORITATIVE DeviceStateManager posture, and only once it has SETTLED (so a
        // hinge pass-through cannot qualify). This also lets a *resting* half-open posture
        // present, which is the whole point of the posture gate.
        val settledUsable = postureGate.isUsable
        if (settledUsable) {
            Log.i(TAG, "Device posture is stably usable (${postureGate.settledPosture}) while on standby (attempt=$attempt); enabling State 4 and presenting.")
            enableDualScreenAndPresent()
            return
        }

        // Not settled-usable yet. Feed the current posture through the gate so the settle
        // window can start/continue, and retry once it resolves.
        val rawPosture = authoritativePostureOrNull()
        if (rawPosture == FoldPosture.OPENED || rawPosture == FoldPosture.TRANSITIONAL) {
            Log.i(TAG, "Posture $rawPosture not yet settled while on standby; awaiting settle (attempt=$attempt).")
            scheduleSettledPostureCheck {
                if (isStopping || userManuallyStopped) return@scheduleSettledPostureCheck
                holdStandbyUntilSecondaryDisplayAvailable(0)
            }
            return
        }

        // Device is folded (or unresolved).
        //
        // If this is a PERSISTENT standby (entered because the device folded while
        // auto-start-on-unfold is enabled), we must keep the service alive indefinitely:
        // the Activity/ViewModel is destroyed on fold, so this service is the only component
        // left that can detect the next unfold and auto-present. We therefore keep re-checking
        // periodically instead of ever giving up.
        //
        // For a transient standby (started via START while the device has not yet settled),
        // we wait a bounded time and then shut down cleanly. startForeground() was already
        // called, so stopSelf() here cannot trigger a foreground-service crash.
        if (isPersistentStandby) {
            serviceScope.launch {
                delay(STANDBY_RETRY_INTERVAL_MS)
                holdStandbyUntilSecondaryDisplayAvailable(0)
            }
            return
        }

        if (attempt < STANDBY_MAX_ATTEMPTS) {
            serviceScope.launch {
                delay(STANDBY_RETRY_INTERVAL_MS)
                holdStandbyUntilSecondaryDisplayAvailable(attempt + 1)
            }
        } else {
            Log.i(TAG, "Standby held while folded; stopping service cleanly (no presentation needed).")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Enter a persistent standby: keep the service alive (and the DeviceStateManager callback
     * registered) across a folded period so the next unfold can be auto-detected and presented.
     */
    private fun enterPersistentStandby() {
        startForegroundWithNotification(isStandby = true)
        if (isPersistentStandby) {
            // A persistent standby loop is already running; nothing more to do.
            return
        }
        isPersistentStandby = true
        Log.i(TAG, "Entering persistent standby (service will survive across folds).")
        holdStandbyUntilSecondaryDisplayAvailable(0)
    }

    private fun exitPersistentStandby() {
        if (isPersistentStandby) {
            Log.i(TAG, "Exiting persistent standby.")
        }
        isPersistentStandby = false
    }

    private fun startForegroundWithNotification(isStandby: Boolean = false) {
        createNotificationChannel()
        val notification = buildNotification(isStandby = isStandby)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _isServiceRunning.value = true
    }

    /**
     * Build minimal, low-profile foreground notification without intrusive banners
     * or redundant close buttons. Clicking notification simply opens MainActivity.
     */
    private fun buildNotification(isStandby: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isStandby) "双屏并发 · 后台待命" else "双屏并发 · 外屏常驻运行中"
        val content = if (isStandby) "展开大屏时将自动开启外屏" else "双屏并发正在运行，外屏常驻显示"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }

    private fun enableDualScreenAndPresent() {
        if (isStopping) return
        // Anti-mistouch gate: State 4 must only be requested when the device is in a usable
        // posture (steady OPENED, or a *settled* resting half-open posture). NOTE: we
        // deliberately do NOT require a secondary display to already exist here — activating
        // State 4 (CONCURRENT_INNER_DEFAULT) is precisely what causes Samsung to power on and
        // enumerate the cover display. Requiring it first would deadlock.
        //
        // We prefer the authoritative, SETTLED posture (which cannot blip during a fold),
        // falling back to the geometric check only when the DSM has no state yet.
        val rawPosture = authoritativePostureOrNull()
        if (rawPosture == FoldPosture.CLOSED) {
            Log.i(TAG, "Device authoritatively folded; canceling standby and refusing State 4.")
            // We are folded: make sure we never hold a stale settled verdict.
            postureGate.reset()
            return
        }
        val usable = postureGate.isUsable ||
            rawPosture?.isUsableForCoverDisplay == true ||
            DeviceStateMonitor.isDeviceUnfolded(applicationContext)
        if (!usable) {
            Log.w(TAG, "Device posture not usable ($rawPosture); refusing to enable State 4.")
            return
        }
        serviceScope.launch {
            try {
                Log.d(TAG, "Requesting State 4 via Shizuku...")
                ShizukuHelper.setConcurrentDisplayState(true, packageName, applicationContext, postureValidated = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable State 4", e)
            }
            // The cover display now needs a moment to power on and be enumerated.
            // checkAndShowPresentationWithRetry() waits for it (and re-verifies the
            // full crash-safe precondition before creating any window).
            if (!isStopping) {
                checkAndShowPresentationWithRetry(retries = 20, delayMs = 250L)
            }
        }
    }

    private fun checkAndShowPresentationWithRetry(retries: Int = 3, delayMs: Long = 250L) {
        if (isStopping) return
        serviceScope.launch {
            for (attempt in 0..retries) {
                if (isStopping) return@launch
                if (activeOverlay?.isShowing == true) return@launch
                val shown = checkAndShowPresentation()
                if (shown) return@launch
                if (attempt < retries) {
                    delay(delayMs)
                }
            }
        }
    }

    private fun checkAndShowPresentation(): Boolean {
        if (isStopping) return false
        val dm = displayManager ?: return false
        val secondaryDisplays = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondaryDisplays.isEmpty()) {
            Log.d(TAG, "No secondary displays yet found in DisplayManager")
            return false
        }

        // Crash-safety hard gate: never attempt to render on a secondary display while the
        // device is folded. When folded the only display IS Display 0, so entering this path
        // would mount a window onto the display the app currently occupies and crash.
        //
        // The authoritative posture is preferred; the geometric heuristic is only a fallback
        // when the DSM has not resolved a state yet. Because a secondary display physically
        // exists at this point, the guard effectively reduces to "is the device folded?".
        // A settled resting half-open posture is allowed (that is the point of the gate).
        val rawPosture = authoritativePostureOrNull()
        if (rawPosture == FoldPosture.CLOSED) {
            Log.w(TAG, "Refusing to present: device is authoritatively folded")
            return false
        }
        if (rawPosture == null && !DeviceStateMonitor.isSafeToPresentOnSecondaryDisplay(applicationContext)) {
            Log.w(TAG, "Refusing to present: device is folded or no safe secondary display target")
            return false
        }

        val targetDisplay = secondaryDisplays.first()
        val freshDisplay = dm.getDisplay(targetDisplay.displayId)
        if (freshDisplay == null || !freshDisplay.isValid) {
            Log.w(TAG, "Target display ${targetDisplay.displayId} is not valid yet.")
            return false
        }

        if (activeOverlay?.isShowing == true && activeDisplayId == freshDisplay.displayId) {
            Log.d(TAG, "Overlay already active on display ${freshDisplay.displayId}")
            return true
        }

        dismissPresentation()

        return try {
            Log.i(TAG, "Showing CoverDisplayOverlayController on display ${freshDisplay.displayId} (${freshDisplay.name}), keepScreenOn=${preferences.keepScreenOn}")
            val overlay = CoverDisplayOverlayController(
                serviceContext = this@CoverDisplayService,
                display = freshDisplay,
                configFlow = DualScreenConfigRepository.config,
                keepScreenOn = preferences.keepScreenOn
            )
            val shown = overlay.show()
            if (shown) {
                activeOverlay = overlay
                activeDisplayId = freshDisplay.displayId
                true
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
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show on cover display", e)
            false
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
        if (isStopping) return
        isStopping = true
        isPersistentStandby = false
        dismissPresentation()
        serviceScope.launch {
            try {
                Log.d(TAG, "Resetting device state via Shizuku...")
                ShizukuHelper.setConcurrentDisplayState(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset device state via Shizuku", e)
            } finally {
                _isServiceRunning.value = false
                DualScreenConfigRepository.updateConfig { it.copy(isPresentationEnabled = false) }
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
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CoverDisplayService onDestroy")
        presentationRecheckJob?.cancel()
        presentationRecheckJob = null
        cancelSettledPostureCheck()
        dismissPresentation()

        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering screenReceiver", e)
            }
            isScreenReceiverRegistered = false
        }

        deviceStateMonitor.stopListening()
        displayManager?.unregisterDisplayListener(displayListener)
        _isServiceRunning.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CoverDisplayService"
        private const val CHANNEL_ID = "cover_display_channel"
        private const val NOTIFICATION_ID = 2024

        // Standby retry tuning: ~250ms steps, up to ~5s total, giving DisplayManager plenty
        // of time to enumerate the cover display after an unfold.
        private const val STANDBY_MAX_ATTEMPTS = 20
        private const val STANDBY_RETRY_INTERVAL_MS = 250L

        // How long the authoritative unfolded state must persist before the service trusts it
        // as a genuine unfold. Matches the ViewModel's UNFOLD_SETTLE_MS (Direction 4.1).
        private const val UNFOLD_SETTLE_MS = 600L

        // How long a half-open / tent posture must persist before it is trusted as a *resting*
        // posture (rather than a momentary hinge pass-through during a fold/unfold). Chosen
        // comfortably longer than the ~<500ms a physical hinge movement spends in the
        // intermediate state, based on real-device traces (SM-F9760).
        private const val HALF_OPEN_SETTLE_MS = 900L

        // Grace period after the cover display disappears before we decide whether it was a
        // physical fold (keep standby) or Samsung's [关闭] action (stop). Absorbs the
        // non-deterministic ordering of the DSM callback vs. the DisplayManager removal.
        private const val DISPLAY_REMOVAL_GRACE_MS = 800L

        // Debounce for the wake/unlock re-presentation path (coalesces SCREEN_ON + USER_PRESENT).
        private const val PRESENT_RECHECK_DEBOUNCE_MS = 300L

        const val ACTION_START = "com.getup.oneui.action.START_COVER_DISPLAY"
        const val ACTION_STOP = "com.getup.oneui.action.STOP_COVER_DISPLAY"
        const val ACTION_TOGGLE = "com.getup.oneui.action.TOGGLE_COVER_DISPLAY"
        const val ACTION_UPDATE_KEEP_SCREEN_ON = "com.getup.oneui.action.UPDATE_KEEP_SCREEN_ON"
        const val ACTION_UPDATE_AUTO_START = "com.getup.oneui.action.UPDATE_AUTO_START"

        const val EXTRA_KEEP_SCREEN_ON = "extra_keep_screen_on"
        const val EXTRA_AUTO_START = "extra_auto_start"

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

        fun setKeepScreenOn(context: Context, enabled: Boolean) {
            val intent = Intent(context, CoverDisplayService::class.java).apply {
                action = ACTION_UPDATE_KEEP_SCREEN_ON
                putExtra(EXTRA_KEEP_SCREEN_ON, enabled)
            }
            if (_isServiceRunning.value) {
                context.startService(intent)
            }
        }

        fun setAutoStartOnUnfold(context: Context, enabled: Boolean) {
            val intent = Intent(context, CoverDisplayService::class.java).apply {
                action = ACTION_UPDATE_AUTO_START
                putExtra(EXTRA_AUTO_START, enabled)
            }
            if (_isServiceRunning.value) {
                context.startService(intent)
            }
        }
    }
}
