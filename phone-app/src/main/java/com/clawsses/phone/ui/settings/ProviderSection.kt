package com.clawsses.phone.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val PROVIDER_OPENCLAW = "openclaw"
private const val PROVIDER_OPENROUTER = "openrouter"

/**
 * Popular OpenRouter models with display names.
 */
data class OpenRouterModel(val id: String, val displayName: String)

val OPENROUTER_MODELS = listOf(
    OpenRouterModel("anthropic/claude-sonnet-4", "Claude Sonnet 4"),
    OpenRouterModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet"),
    OpenRouterModel("openai/gpt-4o", "GPT-4o"),
    OpenRouterModel("openai/gpt-4o-mini", "GPT-4o Mini"),
    OpenRouterModel("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash"),
    OpenRouterModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash"),
    OpenRouterModel("deepseek/deepseek-chat-v3-0324", "DeepSeek V3"),
    OpenRouterModel("moonshotai/kimi-k2.5", "Kimi K2.5"),
    OpenRouterModel("minimax/minimax-m2.5", "MiniMax M2.5"),
    OpenRouterModel("z-ai/glm-4.5-air", "GLM-4.5 Air (Free)"),
)

@Composable
fun ProviderSection(
    selectedProvider: String,
    openRouterApiKey: String,
    openRouterModel: String,
    openClawHost: String,
    openClawPort: String,
    openClawToken: String,
    onProviderChange: (String) -> Unit,
    onOpenRouterApiKeyChange: (String) -> Unit,
    onOpenRouterModelChange: (String) -> Unit,
    onApplyGatewaySettings: (host: String, port: String, token: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // Local form state for gateway settings
    var formHost by remember { mutableStateOf(openClawHost) }
    var formPort by remember { mutableStateOf(openClawPort) }
    var formToken by remember { mutableStateOf(openClawToken) }
    var gatewayTokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(openClawHost, openClawPort, openClawToken) {
        formHost = openClawHost
        formPort = openClawPort
        formToken = openClawToken
    }

    val isGatewayDirty = formHost != openClawHost || formPort != openClawPort || formToken != openClawToken

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Provider radio buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderChange(PROVIDER_OPENCLAW) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = selectedProvider == PROVIDER_OPENCLAW,
                        onClick = { onProviderChange(PROVIDER_OPENCLAW) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OpenClaw Gateway",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Connect to your OpenClaw Gateway (default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderChange(PROVIDER_OPENROUTER) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = selectedProvider == PROVIDER_OPENROUTER,
                        onClick = { onProviderChange(PROVIDER_OPENROUTER) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OpenRouter",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Direct access to AI models via OpenRouter API",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Gateway settings (when OpenClaw selected)
                AnimatedVisibility(
                    visible = selectedProvider == PROVIDER_OPENCLAW,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = formHost,
                                onValueChange = { formHost = it },
                                label = { Text("Host") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = formPort,
                                onValueChange = { formPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = formToken,
                            onValueChange = { formToken = it },
                            label = { Text("Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (gatewayTokenVisible)
                                androidx.compose.ui.text.input.VisualTransformation.None
                            else
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { gatewayTokenVisible = !gatewayTokenVisible }) {
                                    Icon(
                                        imageVector = if (gatewayTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (gatewayTokenVisible) "Hide" else "Show",
                                    )
                                }
                            },
                        )

                        AnimatedVisibility(visible = isGatewayDirty, enter = fadeIn(), exit = fadeOut()) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { onApplyGatewaySettings(formHost, formPort, formToken) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Apply Gateway Settings")
                                }
                            }
                        }
                    }
                }

                // OpenRouter-specific fields
                AnimatedVisibility(
                    visible = selectedProvider == PROVIDER_OPENROUTER,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))

                        // API Key
                        OutlinedTextField(
                            value = openRouterApiKey,
                            onValueChange = onOpenRouterApiKeyChange,
                            label = { Text("OpenRouter API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible)
                                androidx.compose.ui.text.input.VisualTransformation.None
                            else
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                    )
                                }
                            },
                            supportingText = {
                                Text("Get your key at openrouter.ai/keys")
                            },
                        )

                        Spacer(Modifier.height(8.dp))

                        // Model selector
                        val currentModelName = OPENROUTER_MODELS
                            .firstOrNull { it.id == openRouterModel }
                            ?.displayName
                            ?: openRouterModel.ifEmpty { "Select model..." }

                        Text(
                            text = "Model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )

                        OutlinedButton(
                            onClick = { modelDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(currentModelName)
                        }

                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            OPENROUTER_MODELS.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (model.id == openRouterModel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(end = 8.dp),
                                                )
                                            }
                                            Column {
                                                Text(model.displayName)
                                                Text(
                                                    model.id,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onOpenRouterModelChange(model.id)
                                        modelDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
