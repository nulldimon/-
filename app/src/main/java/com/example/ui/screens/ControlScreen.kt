package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ui.GolosGidUiState
import com.example.ui.components.GiantMicToggleButton
import com.example.ui.components.HeroVoiceStatusBanner
import com.example.ui.components.QuickActionTile
import com.example.voice.ListeningStatus
import com.example.voice.ModelInstallState

@Composable
fun ControlScreen(
    uiState: GolosGidUiState,
    onToggleMic: () -> Unit,
    onStopService: () -> Unit,
    onExecutePhrase: (String) -> Unit,
    onOpenSetupTab: () -> Unit,
    onSpeakSummary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var manualPhrase by rememberSaveable { mutableStateOf("") }
    val needsSetup = !uiState.readiness.hasMicPermission ||
        !uiState.readiness.isAccessibilityEnabled ||
        !uiState.readiness.canDrawOverlays ||
        (!uiState.canListenOfflineOrSystem && uiState.modelState !is ModelInstallState.Ready)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroVoiceStatusBanner(
                    status = uiState.runtime.listeningStatus,
                    engineKind = uiState.runtime.activeEngine,
                    partialTranscript = uiState.runtime.partialTranscript,
                    lastFeedback = uiState.runtime.lastActionFeedback
                )
            }

            if (uiState.runtime.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = uiState.runtime.errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                GiantMicToggleButton(
                    status = uiState.runtime.listeningStatus,
                    onClick = onToggleMic
                )
            }

            item {
                AnimatedVisibility(visible = uiState.runtime.listeningStatus != ListeningStatus.STOPPED) {
                    OutlinedButton(
                        onClick = onStopService,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("stop_service_button")
                    ) {
                        Icon(imageVector = Icons.Filled.StopCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Полностью остановить фоновую службу")
                    }
                }
            }

            item {
                Button(
                    onClick = { onExecutePhrase("позови на помощь") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .testTag("sos_emergency_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.HealthAndSafety,
                        contentDescription = "Кнопка экстренной помощи SOS"
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "SOS — Позови на помощь",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (needsSetup) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Завершите настройку для работы в фоне",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val missingItems = buildList {
                                if (!uiState.readiness.hasMicPermission) add("• Разрешите доступ к микрофону")
                                if (!uiState.readiness.canDrawOverlays) add("• Разрешите «Поверх других окон» — иначе Android/Honor блокирует открытие YouTube из свёрнутого режима")
                                if (uiState.modelState !is ModelInstallState.Ready) add("• Скачайте офлайн-модель русской речи (Vosk)")
                                if (!uiState.readiness.isAccessibilityEnabled) add("• Включите Спецвозможности для закрытия YouTube словом «Закрой»")
                            }.joinToString("\n")
                            Text(
                                text = missingItems,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = onOpenSetupTab,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("go_to_setup_button")
                            ) {
                                Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Открыть мастер настройки")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Быстрые кнопки (крупные для касания)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
            }

            item {
                if (isWide) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Закрыть Ютуб",
                                subtitle = "Голосовая команда: «Закрой»",
                                icon = Icons.Filled.Close,
                                accentColor = MaterialTheme.colorScheme.error,
                                testTagName = "quick_close_youtube",
                                onClick = { onExecutePhrase("закрой") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Ютуб: Аудиокниги",
                                subtitle = "«Открой ютуб и найди аудиокниги»",
                                icon = Icons.Filled.PlayCircle,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "quick_youtube_audiobooks",
                                onClick = { onExecutePhrase("открой ютуб и найди аудиокниги") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Громче",
                                subtitle = "Прибавить звук планшета",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_volume_up",
                                onClick = { onExecutePhrase("громче") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Тише",
                                subtitle = "Убавить звук планшета",
                                icon = Icons.AutoMirrored.Filled.VolumeDown,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_volume_down",
                                onClick = { onExecutePhrase("тише") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Пауза / Пуск",
                                subtitle = "Остановить или продолжить видео",
                                icon = Icons.Filled.PlayArrow,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                testTagName = "quick_play_pause",
                                onClick = { onExecutePhrase("пауза") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Который час?",
                                subtitle = "Озвучить текущее время",
                                icon = Icons.Filled.AccessTime,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                testTagName = "quick_read_time",
                                onClick = { onExecutePhrase("сколько времени") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Фонарик",
                                subtitle = "Включить или выключить свет",
                                icon = Icons.Filled.FlashlightOn,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "quick_toggle_flashlight",
                                onClick = { onExecutePhrase("переключи фонарик") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Прочитай экран",
                                subtitle = "Озвучить текст на экране",
                                icon = Icons.Filled.RecordVoiceOver,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "quick_read_screen",
                                onClick = { onExecutePhrase("прочитай экран") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Уведомления",
                                subtitle = "«Что я пропустил?»",
                                icon = Icons.Filled.Notifications,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_read_notifications",
                                onClick = { onExecutePhrase("что я пропустил") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Погода",
                                subtitle = "Прогноз на сегодня",
                                icon = Icons.Filled.WbSunny,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_read_weather",
                                onClick = { onExecutePhrase("какая погода") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionTile(
                            title = "Закрыть Ютуб",
                            subtitle = "Голосовая команда: «Закрой»",
                            icon = Icons.Filled.Close,
                            accentColor = MaterialTheme.colorScheme.error,
                            testTagName = "quick_close_youtube",
                            onClick = { onExecutePhrase("закрой") }
                        )
                        QuickActionTile(
                            title = "Ютуб: Аудиокниги",
                            subtitle = "«Открой ютуб и найди аудиокниги»",
                            icon = Icons.Filled.PlayCircle,
                            accentColor = MaterialTheme.colorScheme.primary,
                            testTagName = "quick_youtube_audiobooks",
                            onClick = { onExecutePhrase("открой ютуб и найди аудиокниги") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Громче",
                                subtitle = "Звук +",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_volume_up",
                                onClick = { onExecutePhrase("громче") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Тише",
                                subtitle = "Звук -",
                                icon = Icons.AutoMirrored.Filled.VolumeDown,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_volume_down",
                                onClick = { onExecutePhrase("тише") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Пауза",
                                subtitle = "Видео",
                                icon = Icons.Filled.PlayArrow,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                testTagName = "quick_play_pause",
                                onClick = { onExecutePhrase("пауза") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Время",
                                subtitle = "Озвучить",
                                icon = Icons.Filled.AccessTime,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                testTagName = "quick_read_time",
                                onClick = { onExecutePhrase("сколько времени") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Фонарик",
                                subtitle = "Свет",
                                icon = Icons.Filled.FlashlightOn,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "quick_toggle_flashlight",
                                onClick = { onExecutePhrase("переключи фонарик") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Экран",
                                subtitle = "Прочитать",
                                icon = Icons.Filled.RecordVoiceOver,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "quick_read_screen",
                                onClick = { onExecutePhrase("прочитай экран") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionTile(
                                title = "Уведомления",
                                subtitle = "Пропущенное",
                                icon = Icons.Filled.Notifications,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_read_notifications",
                                onClick = { onExecutePhrase("что я пропустил") },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionTile(
                                title = "Погода",
                                subtitle = "Прогноз",
                                icon = Icons.Filled.WbSunny,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "quick_read_weather",
                                onClick = { onExecutePhrase("какая погода") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Проверить команду текстом",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = manualPhrase,
                            onValueChange = { manualPhrase = it },
                            placeholder = { Text("Например: Открой ютуб и найди классическую музыку") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (manualPhrase.isNotBlank()) {
                                        onExecutePhrase(manualPhrase)
                                        manualPhrase = ""
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (manualPhrase.isNotBlank()) {
                                            onExecutePhrase(manualPhrase)
                                            manualPhrase = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("send_manual_command_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Выполнить команду"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_command_input")
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "История команд",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (uiState.runtime.commandHistory.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val last = uiState.runtime.commandHistory.first()
                                onSpeakSummary("Последняя команда: ${last.spokenText}. Результат: ${last.actionSummary}")
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("speak_last_history_button")
                        ) {
                            Text("Озвучить последнюю")
                        }
                    }
                }
            }

            if (uiState.runtime.commandHistory.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Пока нет выполненных команд. Включите микрофон и скажите: «Открой ютуб и найди сказки» или «Закрой».",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(uiState.runtime.commandHistory, key = { it.id }) { entry ->
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (entry.succeeded) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "«${entry.spokenText}»",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = entry.timestampLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = entry.actionSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
