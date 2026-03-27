package com.clawsses.phone.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.clawsses.phone.glasses.ApkInstaller

@Composable
fun SoftwareUpdateSection(
    installState: ApkInstaller.InstallState,
    sdkConnected: Boolean,
    onInstall: () -> Unit,
    onInstallViaAdb: (String) -> Unit,
    onDetectIp: () -> Unit,
    detectedIp: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        if (!sdkConnected) {
            UnavailableContent()
            return
        }

        when (installState) {
            is ApkInstaller.InstallState.Idle ->
                IdleContent(onInstall, onInstallViaAdb, onDetectIp, detectedIp)

            is ApkInstaller.InstallState.CheckingConnection ->
                ProgressContent("Checking connection...", -1, null, onCancel = null)

            is ApkInstaller.InstallState.InitializingWifiP2P ->
                ProgressContent("Establishing WiFi P2P...", -1, null, onCancel = null)

            is ApkInstaller.InstallState.PreparingApk ->
                ProgressContent("Preparing APK...", -1, null, onCancel = null)

            is ApkInstaller.InstallState.Uploading ->
                ProgressContent(installState.message, installState.progress, "Do not disconnect the glasses", onCancel)

            is ApkInstaller.InstallState.Installing ->
                ProgressContent(installState.message, -1, "Do not disconnect the glasses", onCancel = null)

            is ApkInstaller.InstallState.Success ->
                SuccessContent(installState.message, onInstall)

            is ApkInstaller.InstallState.Error ->
                ErrorContent(installState.message, installState.canRetry, onInstall)
        }
    }
}

@Composable
private fun UnavailableContent() {
    Text(
        "Glasses App",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Connect glasses via Bluetooth to install updates",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IdleContent(
    onInstall: () -> Unit, 
    onInstallViaAdb: (String) -> Unit,
    onDetectIp: () -> Unit,
    detectedIp: String?,
) {
    var adbIp by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }

    // Auto-fill when IP is detected
    LaunchedEffect(detectedIp) {
        if (!detectedIp.isNullOrEmpty() && detectedIp != adbIp) {
            adbIp = detectedIp
            isDetecting = false
        }
    }

    Text(
        "Glasses App",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Install the glasses app to your Rokid glasses",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    // SDK install (WiFi P2P via CXR)
    Button(
        onClick = onInstall,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Install via Bluetooth")
    }

    Spacer(Modifier.height(8.dp))

    // ADB install (over WiFi)
    Text(
        "or install via ADB over WiFi",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = adbIp,
            onValueChange = { adbIp = it },
            label = { Text("Glasses IP") },
            placeholder = { Text("192.168.1.x") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                isDetecting = true
                onDetectIp()
            },
            enabled = !isDetecting,
        ) {
            Text(if (isDetecting) "..." else "Detect")
        }
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = { onInstallViaAdb(adbIp) },
        enabled = adbIp.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Install via ADB")
    }
}

@Composable
private fun ProgressContent(
    message: String,
    progress: Int,
    warning: String?,
    onCancel: (() -> Unit)?,
) {
    Text(
        "Installing Glasses App",
        style = MaterialTheme.typography.bodyLarge,
    )

    Spacer(Modifier.height(12.dp))

    if (progress >= 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (warning != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            warning,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFC107),
        )
    }

    if (onCancel != null) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SuccessContent(message: String, onInstallAgain: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "Installation Complete",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4CAF50),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = onInstallAgain,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Install Again")
    }
}

@Composable
private fun ErrorContent(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = Color(0xFFF44336),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "Installation Failed",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFF44336),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (canRetry) {
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}
