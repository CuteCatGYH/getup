package com.getup.oneui.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

/**
 * Helper object managing Shizuku connection status, permission requests,
 * and elevated shell command execution (such as cmd device_state state 4).
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val SHIZUKU_REQUEST_CODE = 4001
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private var isInitialized = false

    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reflect Shizuku.newProcess method", e)
            null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        _isRunning.value = false
        _isAuthorized.value = false
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Shizuku permission request result: granted=$granted")
                _isAuthorized.value = granted
            }
        }

    /**
     * Initialize Shizuku listeners. Safe to call multiple times.
     */
    fun init(context: Context) {
        checkInstalled(context)
        if (isInitialized) {
            refreshStatus()
            return
        }
        isInitialized = true

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }

        refreshStatus()
    }

    /**
     * Check if the Shizuku manager application is installed on device.
     */
    fun checkInstalled(context: Context): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (e: Exception) {
            false
        }
        _isInstalled.value = installed
        return installed
    }

    /**
     * Refresh Shizuku running and permission status.
     */
    fun refreshStatus() {
        val running = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
        _isRunning.value = running

        val authorized = if (running) {
            try {
                if (Shizuku.isPreV11()) {
                    false
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
        _isAuthorized.value = authorized
        Log.d(TAG, "Shizuku status: running=$running, authorized=$authorized")
    }

    /**
     * Request Shizuku authorization dialog.
     */
    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        if (!_isRunning.value) {
            refreshStatus()
        }
        if (_isRunning.value && !_isAuthorized.value) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (e: Throwable) {
                Log.e(TAG, "Error requesting Shizuku permission", e)
            }
        }
    }

    private fun createProcess(command: String): Process {
        val method = newProcessMethod
            ?: throw IllegalStateException("Shizuku newProcess method unavailable")
        val cmdArray = arrayOf("sh", "-c", command)
        return method.invoke(null, cmdArray, null, null) as Process
    }

    /**
     * Execute shell command via Shizuku with elevated shell (UID 2000) privileges.
     */
    suspend fun execCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext Result.failure(IllegalStateException("Shizuku server is not running"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(SecurityException("Shizuku permission not granted"))
        }

        try {
            val process = createProcess(command)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.d(TAG, "Command succeeded: '$command' -> output='$output'")
                Result.success(output)
            } else {
                Log.e(TAG, "Command failed ($exitCode): '$command' -> error='$error'")
                Result.failure(Exception("Command exit $exitCode: $error"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception running command: '$command'", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronously execute shell command on IO thread.
     */
    fun execCommandBlocking(command: String): Result<String> {
        if (!Shizuku.pingBinder()) {
            return Result.failure(IllegalStateException("Shizuku server is not running"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Shizuku permission not granted"))
        }

        return try {
            val process = createProcess(command)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(output)
            } else {
                Result.failure(Exception("Command exit $exitCode: $error"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Enable or reset dual-screen concurrency state (State 4 = CONCURRENT_INNER_DEFAULT).
     * When [enable] is true: executes 'cmd device_state state 4'.
     * When [enable] is false: executes 'cmd device_state state reset'.
     */
    suspend fun setConcurrentDisplayState(enable: Boolean, packageName: String? = null): Result<String> {
        if (enable) {
            packageName?.let { pkg ->
                execCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")
                execCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            }
            return execCommand("cmd device_state state 4")
        } else {
            return execCommand("cmd device_state state reset")
        }
    }

    /**
     * Query currently active device state identifier (e.g. 0=CLOSED, 2=HALF_OPENED, 3=OPENED, 4=CONCURRENT).
     */
    suspend fun queryDeviceState(): Result<Int> {
        val result = execCommand("cmd device_state print-state")
        return result.mapCatching { out ->
            out.trim().lines().lastOrNull()?.trim()?.toIntOrNull()
                ?: throw IllegalStateException("Cannot parse device state from '$out'")
        }
    }
}
