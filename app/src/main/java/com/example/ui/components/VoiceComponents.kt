package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.voice.ListeningStatus
import com.example.voice.SpeechEngineKind

@Composable
fun HeroVoiceStatusBanner(
    status: ListeningStatus,
    engineKind: SpeechEngineKind,
    partialTranscript: String,
    lastFeedback: String,
    modifier: Modifier = Modifier
) {
    val isListening = status == ListeningStatus.LISTENING
    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            ListeningStatus.LISTENING -> MaterialTheme.colorScheme.primary
            ListeningStatus.ERROR -> MaterialTheme.colorScheme.error
            ListeningStatus.PAUSED -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "banner_border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_voice_wave_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.32f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                when (status) {
                                    ListeningStatus.LISTENING -> MaterialTheme.colorScheme.primary
                                    ListeningStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer
                                    ListeningStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    ) {
                        Icon(
                            imageVector = when (status) {
                                ListeningStatus.LISTENING -> Icons.Filled.Mic
                                ListeningStatus.PAUSED -> Icons.Filled.Pause
                                else -> Icons.Filled.MicOff
                            },
                            contentDescription = null,
                            tint = when (status) {
                                ListeningStatus.LISTENING -> MaterialTheme.colorScheme.onPrimary
                                ListeningStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (status) {
                                ListeningStatus.LISTENING -> "Слушаю в фоне"
                                ListeningStatus.INITIALIZING -> "Загрузка русской модели..."
                                ListeningStatus.PAUSED -> "Микрофон на паузе"
                                ListeningStatus.ERROR -> "Требуется настройка"
                                ListeningStatus.STOPPED -> "Микрофон выключен"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = when (engineKind) {
                                    SpeechEngineKind.VOSK_OFFLINE -> "VOSK ОФЛАЙН RU • БЕЗ GOOGLE"
                                    SpeechEngineKind.SYSTEM_RECOGNIZER -> "СИСТЕМНАЯ РЕЧЬ RU"
                                    SpeechEngineKind.NONE -> "HUAWEI БЕЗ GMS • НУЖНА МОДЕЛЬ"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (partialTranscript.isNotBlank()) {
                                "Слышу: «$partialTranscript»"
                            } else {
                                lastFeedback
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (partialTranscript.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GiantMicToggleButton(
    status: ListeningStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = status == ListeningStatus.LISTENING || status == ListeningStatus.INITIALIZING
    val isPaused = status == ListeningStatus.PAUSED

    val containerColor = when {
        isListening -> MaterialTheme.colorScheme.errorContainer
        isPaused -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isListening -> MaterialTheme.colorScheme.onErrorContainer
        isPaused -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val label = when {
        isListening -> "Поставить микрофон на паузу"
        isPaused -> "Продолжить фоновое прослушивание"
        else -> "Включить голос в фоне"
    }
    val subLabel = when {
        isListening -> "Работает даже при открытом YouTube"
        isPaused -> "Нажмите, чтобы снова слышать команды"
        else -> "Нажмите для запуска фонового управления"
    }

    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(2.dp, contentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .minimumInteractiveComponentSize()
            .testTag("mic_toggle_button")
            .semantics {
                contentDescription = "$label. $subLabel"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Pause else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTagName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
        modifier = modifier
            .heightIn(min = 84.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTagName)
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ReadinessBadgeRow(
    title: String,
    isReady: Boolean,
    detail: String,
    actionLabel: String,
    testTagName: String,
    onAction: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.5.dp,
            if (isReady) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = if (isReady) "Готово" else "Требуется действие",
                    tint = if (isReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isReady) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag(testTagName)
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun AccessibilityDisclosureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Зачем нужны Спецвозможности?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "Служба «ГолосГид» использует API AccessibilityService исключительно для того, чтобы по вашей голосовой команде:\n\n" +
                    "• Закрывать YouTube словом «Закрой» и возвращать на главный экран\n" +
                    "• Выполнять жесты «Назад», прокрутку вверх/вниз и открытие первого видео\n\n" +
                    "Приложение не собирает личные данные, не читает пароли и работает локально на планшете.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_accessibility_settings")
            ) {
                Text("Открыть настройки")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("dismiss_accessibility_dialog")
            ) {
                Text("Позже")
            }
        }
    )
}
