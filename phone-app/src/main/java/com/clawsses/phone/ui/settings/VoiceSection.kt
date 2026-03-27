@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clawsses.phone.voice.VoiceLanguageManager
import com.clawsses.phone.voice.VoiceRecognitionManager

/**
 * Available voice recognition providers.
 */
private const val PROVIDER_DEVICE = "device"
private const val PROVIDER_OPENAI = "openai"
private const val PROVIDER_OPENROUTER = "openrouter"

/**
 * Popular OpenRouter models suitable for audio/transcription tasks.
 */
private data class VoiceModel(val id: String, val displayName: String)

private val OPENROUTER_VOICE_MODELS = listOf(
    VoiceModel("openai/whisper-large-v3", "Whisper Large v3"),
    VoiceModel("openai/whisper-1", "Whisper"),
    VoiceModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash"),
    VoiceModel("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash"),
)

@Composable
fun VoiceSection(
    voiceLanguageManager: VoiceLanguageManager,
    voiceRecognitionManager: VoiceRecognitionManager? = null,
    modifier: Modifier = Modifier,
) {
    val availableLanguages by voiceLanguageManager.availableLanguages.collectAsState()
    val selectedLanguage by voiceLanguageManager.selectedLanguage.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val currentLangDisplay = availableLanguages
        .firstOrNull { it.tag == selectedLanguage }
        ?.displayName ?: selectedLanguage.ifEmpty { "Default" }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Voice Provider settings
        if (voiceRecognitionManager != null) {
            VoiceProviderSettings(voiceRecognitionManager)
            Spacer(Modifier.height(12.dp))
        }

        // Language selection
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Recognition Language",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        currentLangDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    if (showSheet) {
        LanguageBottomSheet(
            languages = availableLanguages,
            selectedTag = selectedLanguage,
            preferredLocales = VoiceLanguageManager.PREFERRED_LOCALES,
            onSelect = { tag ->
                voiceLanguageManager.selectLanguage(tag)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun VoiceProviderSettings(
    voiceRecognitionManager: VoiceRecognitionManager,
) {
    // Current provider state
    var selectedProvider by remember { mutableStateOf(voiceRecognitionManager.getVoiceProvider()) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // OpenAI state
    var openAiKey by remember { mutableStateOf(voiceRecognitionManager.getOpenAIApiKey()) }
    var openAiEnabled by remember { mutableStateOf(voiceRecognitionManager.isOpenAIVoiceEnabled()) }

    // OpenRouter state
    var openRouterKey by remember { mutableStateOf(voiceRecognitionManager.getOpenRouterApiKey()) }
    var openRouterModel by remember { mutableStateOf(voiceRecognitionManager.getOpenRouterModel()) }
    var openRouterEnabled by remember { mutableStateOf(voiceRecognitionManager.isOpenRouterVoiceEnabled()) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val currentProvider = selectedProvider
    val isCloudActive = (currentProvider == PROVIDER_OPENAI && openAiEnabled && openAiKey.isNotEmpty()) ||
            (currentProvider == PROVIDER_OPENROUTER && openRouterEnabled && openRouterKey.isNotEmpty())

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (isCloudActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Voice Recognition",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        when {
                            currentProvider == PROVIDER_OPENAI && openAiEnabled && openAiKey.isNotEmpty() -> "OpenAI — GPT-4o"
                            currentProvider == PROVIDER_OPENROUTER && openRouterEnabled && openRouterKey.isNotEmpty() -> {
                                val modelName = OPENROUTER_VOICE_MODELS.firstOrNull { it.id == openRouterModel }?.displayName ?: openRouterModel
                                "OpenRouter — $modelName"
                            }
                            else -> "Device (Android SpeechRecognizer)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCloudActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Provider selection
            Text(
                "Provider",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Device option
            ProviderRow(
                label = "Device",
                description = "Android built-in speech recognition",
                isSelected = currentProvider == PROVIDER_DEVICE,
                onClick = {
                    selectedProvider = PROVIDER_DEVICE
                    voiceRecognitionManager.setVoiceProvider(PROVIDER_DEVICE)
                },
            )

            // OpenAI option
            ProviderRow(
                label = "OpenAI",
                description = "GPT-4o Realtime API",
                isSelected = currentProvider == PROVIDER_OPENAI,
                onClick = {
                    selectedProvider = PROVIDER_OPENAI
                    voiceRecognitionManager.setVoiceProvider(PROVIDER_OPENAI)
                },
            )

            // OpenRouter option
            ProviderRow(
                label = "OpenRouter",
                description = "Whisper, Gemini and more",
                isSelected = currentProvider == PROVIDER_OPENROUTER,
                onClick = {
                    selectedProvider = PROVIDER_OPENROUTER
                    voiceRecognitionManager.setVoiceProvider(PROVIDER_OPENROUTER)
                },
            )

            // OpenAI API key
            AnimatedVisibility(
                visible = currentProvider == PROVIDER_OPENAI,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = openAiKey,
                        onValueChange = { newKey ->
                            openAiKey = newKey
                            voiceRecognitionManager.setOpenAIApiKey(newKey)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenAI API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Hide" else "Show",
                                    )
                                }
                                if (openAiKey.isNotEmpty()) {
                                    IconButton(onClick = {
                                        openAiKey = ""
                                        voiceRecognitionManager.setOpenAIApiKey("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        },
                        supportingText = {
                            if (openAiKey.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), Color(0xFF4CAF50))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Saved", color = Color(0xFF4CAF50))
                                }
                            } else {
                                Text("Required for OpenAI voice recognition")
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Enable cloud recognition",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = openAiEnabled,
                            onCheckedChange = { enabled ->
                                openAiEnabled = enabled
                                voiceRecognitionManager.setOpenAIVoiceEnabled(enabled)
                            },
                            enabled = openAiKey.isNotEmpty(),
                        )
                    }
                }
            }

            // OpenRouter API key + model selector
            AnimatedVisibility(
                visible = currentProvider == PROVIDER_OPENROUTER,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = { newKey ->
                            openRouterKey = newKey
                            voiceRecognitionManager.setOpenRouterApiKey(newKey)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API Key") },
                        placeholder = { Text("sk-or-...") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Hide" else "Show",
                                    )
                                }
                                if (openRouterKey.isNotEmpty()) {
                                    IconButton(onClick = {
                                        openRouterKey = ""
                                        voiceRecognitionManager.setOpenRouterApiKey("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        },
                        supportingText = {
                            if (openRouterKey.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), Color(0xFF4CAF50))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Saved", color = Color(0xFF4CAF50))
                                }
                            } else {
                                Text("Get your key at openrouter.ai/keys")
                            }
                        },
                    )

                    Spacer(Modifier.height(8.dp))

                    // Model selector
                    Text(
                        "Model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    val currentModelName = OPENROUTER_VOICE_MODELS
                        .firstOrNull { it.id == openRouterModel }
                        ?.displayName
                        ?: if (openRouterModel.isNotEmpty()) openRouterModel else "Select model..."

                    OutlinedButton(
                        onClick = { modelDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(currentModelName)
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f),
                    ) {
                        OPENROUTER_VOICE_MODELS.forEach { model ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (model.id == openRouterModel) {
                                            Icon(Icons.Default.Check, null, Modifier.padding(end = 8.dp))
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
                                    openRouterModel = model.id
                                    voiceRecognitionManager.setOpenRouterModel(model.id)
                                    modelDropdownExpanded = false
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Enable cloud recognition",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = openRouterEnabled,
                            onCheckedChange = { enabled ->
                                openRouterEnabled = enabled
                                voiceRecognitionManager.setOpenRouterVoiceEnabled(enabled)
                            },
                            enabled = openRouterKey.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        androidx.compose.material3.RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageBottomSheet(
    languages: List<VoiceLanguageManager.LanguageOption>,
    selectedTag: String,
    preferredLocales: List<java.util.Locale>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val preferredTags = preferredLocales.map { it.toLanguageTag() }.toSet()
    val preferred = languages.filter { it.tag in preferredTags }
    val remaining = languages.filter { it.tag !in preferredTags }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                "Recognition Language",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (preferred.isNotEmpty()) {
                items(preferred) { lang ->
                    LanguageRow(lang, lang.tag == selectedTag, onSelect)
                }
                item {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(remaining) { lang ->
                LanguageRow(lang, lang.tag == selectedTag, onSelect)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun LanguageRow(
    language: VoiceLanguageManager.LanguageOption,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(language.tag) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            language.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            language.tag,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
