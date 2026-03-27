package com.clawsses.phone.glasses

import android.content.Context
import android.util.Log
import dadb.Dadb
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay

/**
 * Handles APK installation on Rokid glasses.
 *
 * Supports two installation methods:
 * 1. SDK Method: Uses CXR-M SDK's WiFi P2P transfer (requires SDK initialization + Bluetooth connection)
 * 2. ADB Method: Uses ADB over WiFi (requires glasses to have ADB enabled and be on same network)
 *
 * The ADB method is more reliable for development and doesn't require the full SDK setup.
 */
class ApkInstaller(private val context: Context) {

    companion object {
        private const val TAG = "ApkInstaller"
        private const val GLASSES_APP_ASSET = "glasses-app-release.apk"
        private const val DEFAULT_ADB_PORT = 5555
        private const val OPERATION_TIMEOUT_MS = 60_000L
    }

    /**
     * Installation method
     */
    enum class InstallMethod {
        SDK,    // Use Rokid CXR-M SDK (WiFi P2P)
        ADB     // Use ADB over WiFi
    }

    /**
     * Installation state machineIt
     */
    sealed class InstallState {
        object Idle : InstallState()
        object CheckingConnection : InstallState()
        object InitializingWifiP2P : InstallState()
        object PreparingApk : InstallState()
        data class Uploading(val message: String = "Uploading APK...", val progress: Int = -1) : InstallState()
        data class Installing(val message: String = "Installing...") : InstallState()
        data class Success(val message: String = "Installation complete!") : InstallState()
        data class Error(val message: String, val canRetry: Boolean = true) : InstallState()
    }

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var installJob: Job? = null

    // ADB connection settings
    private var adbHost: String = ""
    private var adbPort: Int = DEFAULT_ADB_PORT

    /**
     * Configure ADB connection for installation.
     * Call this before using installViaAdb().
     */
    fun configureAdb(host: String, port: Int = DEFAULT_ADB_PORT) {
        this.adbHost = host
        this.adbPort = port
        Log.d(TAG, "ADB configured: $host:$port")
    }

    /**
     * Install the glasses app using ADB over WiFi.
     * This is more reliable for development than the SDK method.
     *
     * Prerequisites on glasses:
     * 1. Enable Developer Options (tap Build Number 7 times)
     * 2. Enable USB debugging
     * 3. Connect glasses to same WiFi network as phone
     * 4. Find glasses IP: Settings > About > IP address
     */
    fun installViaAdb(host: String? = null, port: Int? = null) {
        val targetHost = host ?: adbHost
        val targetPort = port ?: adbPort

        if (targetHost.isEmpty()) {
            _installState.value = InstallState.Error(
                "ADB host not configured. Go to Settings and enter the glasses IP address.",
                canRetry = false
            )
            return
        }

        if (!canStartInstall()) return

        Log.i(TAG, "Starting ADB installation to $targetHost:$targetPort")
        _installState.value = InstallState.CheckingConnection

        installJob = scope.launch {
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    doAdbInstall(targetHost, targetPort)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Installation timed out after ${OPERATION_TIMEOUT_MS}ms")
                _installState.value = InstallState.Error("Installation timed out. Check glasses connection.")
            } catch (e: CancellationException) {
                Log.d(TAG, "Installation cancelled")
                _installState.value = InstallState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed", e)
                _installState.value = InstallState.Error(formatError(e))
            }
        }
    }

    private suspend fun doAdbInstall(host: String, port: Int) = withContext(Dispatchers.IO) {
        // Step 1: Test connection
        Log.d(TAG, "Testing ADB connection to $host:$port...")
        _installState.value = InstallState.CheckingConnection

        val dadb = try {
            Dadb.create(host, port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ADB", e)
            throw Exception("Cannot connect to glasses at $host:$port. " +
                "Ensure ADB debugging is enabled and glasses are on the same network.")
        }

        dadb.use { adb ->
            // Verify connection works
            val testResult = adb.shell("echo connected")
            if (testResult.exitCode != 0) {
                throw Exception("ADB connection test failed. Check if glasses accepted the connection.")
            }
            Log.d(TAG, "ADB connection verified")

            // Step 2: Prepare APK
            _installState.value = InstallState.PreparingApk
            val apkFile = extractApkFromAssets()
                ?: throw Exception("No APK found. Ensure glasses-app-release.apk is bundled.")

            Log.d(TAG, "APK prepared: ${apkFile.absolutePath} (${apkFile.length() / 1024} KB)")

            // Step 3: Install APK
            _installState.value = InstallState.Uploading("Uploading ${apkFile.length() / 1024} KB...")

            try {
                Log.d(TAG, "Installing APK via ADB...")
                _installState.value = InstallState.Installing("Installing on glasses...")
                adb.install(apkFile, "-r") // -r = replace existing

                Log.i(TAG, "APK installation successful!")
                _installState.value = InstallState.Success("Glasses app installed successfully!")

            } catch (e: Exception) {
                Log.e(TAG, "APK installation failed", e)
                throw Exception("Installation failed: ${e.message}")
            } finally {
                // Cleanup temp file
                cleanupTempApk()
            }
        }
    }

    /**
     * Install the bundled glasses app APK using SDK method (WiFi P2P).
     * Requires: SDK initialized, Bluetooth connected to glasses.
     *
     * The SDK will automatically establish WiFi P2P for the transfer.
     */
    fun installViaSdk() {
        if (!canStartInstall()) return

        // Check SDK initialization
        if (!RokidSdkManager.isReady()) {
            Log.e(TAG, "Rokid SDK not initialized")
            _installState.value = InstallState.Error(
                "Rokid SDK not initialized. Check if credentials are configured in local.properties.",
                canRetry = false
            )
            return
        }

        // Check Bluetooth connection
        if (!RokidSdkManager.isConnected()) {
            Log.e(TAG, "Not connected to glasses via Bluetooth")
            _installState.value = InstallState.Error(
                "Not connected to glasses. Connect to glasses via Bluetooth first.",
                canRetry = true
            )
            return
        }

        Log.i(TAG, "Starting SDK installation via WiFi P2P")
        _installState.value = InstallState.PreparingApk

        installJob = scope.launch {
            try {
                withTimeout(OPERATION_TIMEOUT_MS) {
                    doSdkInstall()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "SDK installation timed out after ${OPERATION_TIMEOUT_MS}ms")
                _installState.value = InstallState.Error("Installation timed out. Check glasses connection.")
            } catch (e: CancellationException) {
                Log.d(TAG, "SDK installation cancelled")
                _installState.value = InstallState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "SDK installation failed", e)
                _installState.value = InstallState.Error(formatError(e))
            }
        }
    }

    private suspend fun doSdkInstall() = withContext(Dispatchers.IO) {
        // Step 1: Initialize WiFi P2P if not already connected
        if (!RokidSdkManager.isWifiP2PConnected()) {
            Log.d(TAG, "Initializing WiFi P2P for APK transfer...")
            _installState.value = InstallState.InitializingWifiP2P

            val p2pStarted = withContext(Dispatchers.Main) {
                RokidSdkManager.initWifiP2P()
            }
            if (!p2pStarted) {
                throw Exception("Failed to initialize WiFi P2P. Check that WiFi is enabled on your phone.")
            }

            // Wait for WiFi P2P connection (with timeout)
            val p2pTimeoutMs = 30_000L
            val startTime = System.currentTimeMillis()
            while (!RokidSdkManager.isWifiP2PConnected()) {
                if (System.currentTimeMillis() - startTime > p2pTimeoutMs) {
                    throw Exception("WiFi P2P connection timed out after ${p2pTimeoutMs / 1000}s")
                }
                delay(500)
            }
            Log.d(TAG, "WiFi P2P connected")
        }

        // Step 2: Prepare APK
        _installState.value = InstallState.PreparingApk
        val apkFile = extractApkFromAssets()
            ?: throw Exception("No APK found. Ensure glasses-app-release.apk is bundled.")

        Log.d(TAG, "APK prepared: ${apkFile.absolutePath} (${apkFile.length() / 1024} KB)")

        // Step 3: Upload APK via SDK
        _installState.value = InstallState.Uploading("Uploading ${apkFile.length() / 1024} KB via WiFi P2P...")

        val uploadResult = withContext(Dispatchers.Main) {
            RokidSdkManager.startUploadApk(apkFile.absolutePath)
        }

        if (!uploadResult) {
            throw Exception("Failed to start APK upload via SDK")
        }

        // Wait for upload to complete (listen to state callbacks)
        val uploadTimeoutMs = 60_000L
        val uploadStart = System.currentTimeMillis()
        var uploadDone = false
        var uploadSuccess = false

        // Register one-shot callbacks for upload result
        val origSuccess = RokidSdkManager.onApkUploadSucceed
        val origFailed = RokidSdkManager.onApkUploadFailed
        RokidSdkManager.onApkUploadSucceed = {
            uploadDone = true
            uploadSuccess = true
        }
        RokidSdkManager.onApkUploadFailed = {
            uploadDone = true
            uploadSuccess = false
        }

        try {
            while (!uploadDone) {
                if (System.currentTimeMillis() - uploadStart > uploadTimeoutMs) {
                    RokidSdkManager.stopUploadApk()
                    throw Exception("APK upload timed out")
                }
                delay(500)
            }

            if (!uploadSuccess) {
                throw Exception("APK upload failed on glasses")
            }

            Log.i(TAG, "APK upload successful")
            _installState.value = InstallState.Success("Glasses app installed via WiFi P2P!")

        } finally {
            // Restore original callbacks
            RokidSdkManager.onApkUploadSucceed = origSuccess
            RokidSdkManager.onApkUploadFailed = origFailed
            // Cleanup temp APK
            cleanupTempApk()
            // Disconnect WiFi P2P after install to free resources
            disconnectWifiP2PAfterInstall()
        }
    }

    /**
     * Disconnect WiFi P2P after APK installation is complete.
     * Frees the WiFi P2P resource so it doesn't interfere with normal BT operation.
     * Safe to call even if WiFi P2P was never connected.
     */
    private fun disconnectWifiP2PAfterInstall() {
        try {
            RokidSdkManager.deinitWifiP2P()
            Log.d(TAG, "WiFi P2P disconnected after APK install")
        } catch (e: Exception) {
            Log.w(TAG, "WiFi P2P disconnect after install failed (non-fatal): ${e.message}")
        }
    }


    /**
     * Legacy method for backwards compatibility.
     * Tries ADB first if configured, otherwise falls back to SDK method.
     * Both installation methods are available — ADB is preferred for dev speed,
     * SDK (WiFi P2P) works without knowing the glasses IP address.
     */
    fun installGlassesApp() {
        if (!canStartInstall()) return

        // Prefer ADB method (faster, more reliable for dev)
        if (adbHost.isNotEmpty()) {
            installViaAdb()
        } else {
            // No ADB configured — try SDK WiFi P2P method
            installViaSdk()
        }
    }

    /**
     * Test ADB connection to glasses without installing.
     */
    fun testAdbConnection(host: String, port: Int = DEFAULT_ADB_PORT, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Testing ADB connection to $host:$port")
                    Dadb.create(host, port).use { adb ->
                        val result = adb.shell("getprop ro.product.model")
                        if (result.exitCode == 0) {
                            val model = result.output.trim()
                            Log.d(TAG, "ADB connection successful: $model")
                            onResult(true, "Connected to: $model")
                        } else {
                            onResult(false, "Connection failed: ${result.output}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ADB connection test failed", e)
                onResult(false, formatError(e))
            }
        }
    }

    /**
     * Cancel the current installation.
     */
    fun cancelInstallation() {
        Log.d(TAG, "Cancelling installation")
        installJob?.cancel()
        installJob = null
        cleanupTempApk()
        _installState.value = InstallState.Idle
    }

    /**
     * Reset state to idle.
     */
    fun resetState() {
        _installState.value = InstallState.Idle
        _lastError.value = null
    }

    private fun canStartInstall(): Boolean {
        val currentState = _installState.value
        if (currentState !is InstallState.Idle &&
            currentState !is InstallState.Error &&
            currentState !is InstallState.Success) {
            Log.w(TAG, "Installation already in progress: $currentState")
            return false
        }
        return true
    }

    private fun extractApkFromAssets(): File? {
        return try {
            val cacheDir = context.cacheDir
            val apkFile = File(cacheDir, "glasses-app.apk")

            // Check if we have a bundled APK in assets
            val assetManager = context.assets
            val assetList = assetManager.list("") ?: emptyArray()

            if (GLASSES_APP_ASSET in assetList) {
                Log.d(TAG, "Extracting bundled APK from assets")
                assetManager.open(GLASSES_APP_ASSET).use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }
                apkFile
            } else {
                // Check debug APK location
                val debugApk = File(cacheDir, "glasses-app-debug.apk")
                if (debugApk.exists()) {
                    Log.d(TAG, "Using debug APK from cache: ${debugApk.absolutePath}")
                    return debugApk
                }

                // Check external files directory
                val externalApk = File(context.getExternalFilesDir(null), "glasses-app.apk")
                if (externalApk.exists()) {
                    Log.d(TAG, "Using APK from external files: ${externalApk.absolutePath}")
                    externalApk
                } else {
                    Log.e(TAG, "No glasses-app APK found. Checked: assets/$GLASSES_APP_ASSET, $debugApk, $externalApk")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting APK from assets", e)
            null
        }
    }

    private fun cleanupTempApk() {
        try {
            File(context.cacheDir, "glasses-app.apk").delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean up cached APK", e)
        }
    }

    private fun formatError(e: Exception): String {
        val message = e.message ?: "Unknown error"
        return when {
            message.contains("Connection refused") ->
                "Connection refused. Ensure:\n" +
                "1. ADB debugging is enabled on glasses\n" +
                "2. Glasses are on the same WiFi network\n" +
                "3. IP address is correct"
            message.contains("timeout", ignoreCase = true) ->
                "Connection timed out. Check:\n" +
                "1. Glasses IP address\n" +
                "2. WiFi connectivity\n" +
                "3. Firewall settings"
            message.contains("INSTALL_FAILED") ->
                "Installation failed: $message\n" +
                "Try uninstalling the existing app first."
            message.contains("No route to host") ->
                "Cannot reach glasses. Ensure they're on the same network."
            else -> message
        }
    }

    fun cleanup() {
        installJob?.cancel()
        scope.cancel()
        cleanupTempApk()
    }
}
