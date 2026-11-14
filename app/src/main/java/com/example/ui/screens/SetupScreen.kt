package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.GolosGidUiState
import com.example.ui.components.AccessibilityDisclosureDialog
import com.example.ui.components.ReadinessBadgeRow
import com.example.voice.ModelInstallState
import kotlin.math.roundToInt

@Composable
fun SetupScreen(
    uiState: GolosGidUiState,
    onRequestPermissions: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDownloadModel: () -> Unit,
    onPickModelZip: () -> Unit,
    onDeleteModel: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleStrictCommands: (Boolean) -> Unit,
    onToggleReturnToApp: (Boolean) -> Unit,
    onToggleSpeakConfirmations: (Boolean) -> Unit,
    onToggleSimpleSpeech: (Boolean) -> Unit,
    onToggleAutoOpenFirst: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSaveWeatherCity: (String) -> Unit,
    onSaveEmergencyContact: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAccessibilityDisclosure by rememberSaveable { mutableStateOf(false) }

    var weatherCityInput by remember(uiState.settings.weatherCity) {
        mutableStateOf(uiState.settings.weatherCity)
    }
    var emergencyNameInput by remember(uiState.settings.emergencyContactName) {
        mutableStateOf(uiState.settings.emergencyContactName)
    }
    var emergencyPhoneInput by remember(uiState.settings.emergencyContactPhone) {
        mutableStateOf(uiState.settings.emergencyContactPhone)
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onConfirm = {
                showAccessibilityDisclosure = false
                onOpenAccessibilitySettings()
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Настройка Huawei / Honor",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Выполните шаги ниже, чтобы голосовое управление работало в фоне, читало экран и уведомления, управляло звонками и закрывало YouTube одним словом.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ReadinessBadgeRow(
                title = "1. Микрофон и уведомления",
                isReady = uiState.readiness.hasMicPermission && uiState.readiness.hasNotificationPermission,
                detail = if (uiState.readiness.hasMicPermission) {
                    "Доступ к микрофону выдан."
                } else {
                    "Нужен доступ к микрофону для распознавания русской речи в фоне."
                },
                actionLabel = "Разрешить микрофон",
                testTagName = "grant_mic_button",
                onAction = onRequestPermissions
            )
        }

        item {
            ReadinessBadgeRow(
                title = "2. Поверх других окон (запуск из фона)",
                isReady = uiState.readiness.canDrawOverlays,
                detail = if (uiState.readiness.canDrawOverlays) {
                    "Разрешено: приложение открывает YouTube и WhatsApp, даже когда свёрнуто."
                } else {
                    "Обязательно для Honor/Huawei! Без этого права система блокирует запуск окон из фона."
                },
                actionLabel = "Разрешить поверх других окон",
                testTagName = "enable_overlay_button",
                onAction = onOpenOverlaySettings
            )
        }

        item {
            ReadinessBadgeRow(
                title = "3. Спецвозможности (YouTube и «Прочитай экран»)",
                isReady = uiState.readiness.isAccessibilityEnabled,
                detail = if (uiState.readiness.isAccessibilityEnabled) {
                    "Активна: работают «Закрой», скорость видео, перемотка, полный экран и «Прочитай экран»."
                } else {
                    "Включите службу «ГолосГид» в разделе Спецвозможности для управления плеером и озвучки экрана."
                },
                actionLabel = "Включить Спецвозможности",
                testTagName = "enable_accessibility_button",
                onAction = { showAccessibilityDisclosure = true }
            )
        }

        item {
            ReadinessBadgeRow(
                title = "4. Контакты (звонки и сообщения)",
                isReady = uiState.readiness.hasContactsPermission,
                detail = if (uiState.readiness.hasContactsPermission) {
                    "Доступ к контактам выдан: работают «Позвони дочери/сыну», «Отправь сообщение»."
                } else {
                    "Нужен доступ к контактам, чтобы находить имена из телефонной книги."
                },
                actionLabel = "Разрешить контакты",
                testTagName = "grant_contacts_button",
                onAction = onRequestContactsPermission
            )
        }

        item {
            ReadinessBadgeRow(
                title = "5. Звонки и журнал вызовов",
                isReady = uiState.readiness.hasPhonePermissions,
                detail = if (uiState.readiness.hasPhonePermissions) {
                    "Разрешено: ответ и сброс звонка, пропущенные вызовы, «Позвони последнему», экстренный вызов."
                } else {
                    "Разрешите доступ к звонкам и журналу вызовов для команд телефона и «Позови на помощь»."
                },
                actionLabel = "Разрешить звонки",
                testTagName = "grant_phone_button",
                onAction = onRequestPhonePermissions
            )
        }

        item {
            ReadinessBadgeRow(
                title = "6. Чтение уведомлений («Что я пропустил?»)",
                isReady = uiState.readiness.hasNotificationListener,
                detail = if (uiState.readiness.hasNotificationListener) {
                    "Активно: ГолосГид озвучивает пропущенные уведомления и входящие сообщения."
                } else {
                    "Включите «ГолосГид» в разделе Доступ к уведомлениям для команд «Что я пропустил?» и «Прочитай сообщения»."
                },
                actionLabel = "Открыть доступ к уведомлениям",
                testTagName = "enable_notif_listener_button",
                onAction = onOpenNotificationListenerSettings
            )
        }

        item {
            ReadinessBadgeRow(
                title = "7. Яркость экрана («Сделай экран ярче»)",
                isReady = uiState.readiness.canWriteSettings,
                detail = if (uiState.readiness.canWriteSettings) {
                    "Разрешено: можно голосом делать экран ярче или темнее."
                } else {
                    "Разрешите изменение системных настроек, чтобы управлять яркостью голосом."
                },
                actionLabel = "Разрешить изменение яркости",
                testTagName = "enable_write_settings_button",
                onAction = onOpenWriteSettings
            )
        }

        item {
            ReadinessBadgeRow(
                title = "8. Блютуз и наушники («Проверь наушники»)",
                isReady = uiState.readiness.hasBluetoothPermission,
                detail = if (uiState.readiness.hasBluetoothPermission) {
                    "Разрешено: можно проверять подключение наушников и открывать настройки Bluetooth."
                } else {
                    "Разрешите доступ к устройствам поблизости, чтобы проверять подключённые наушники и управлять Bluetooth."
                },
                actionLabel = "Разрешить Блютуз",
                testTagName = "grant_bluetooth_button",
                onAction = onRequestBluetoothPermission
            )
        }

        item {
            VoskOfflineModelCard(
                modelState = uiState.modelState,
                systemSpeechAvailable = uiState.readiness.isSystemSpeechAvailable,
                onDownload = onDownloadModel,
                onPickZip = onPickModelZip,
                onDelete = onDeleteModel
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Экстренный контакт («Позови на помощь»)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "При команде «Позови на помощь» помощник сначала спросит подтверждение «Да», чтобы исключить случайный звонок.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = emergencyNameInput,
                        onValueChange = { emergencyNameInput = it },
                        label = { Text("Имя родственника (например: Дочь Ирина)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_name_input")
                    )
                    OutlinedTextField(
                        value = emergencyPhoneInput,
                        onValueChange = { emergencyPhoneInput = it },
                        label = { Text("Номер телефона (например: +79001234567)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_phone_input")
                    )
                    Button(
                        onClick = { onSaveEmergencyContact(emergencyNameInput, emergencyPhoneInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("save_emergency_contact_button")
                    ) {
                        Text("Сохранить экстренный контакт")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Город для прогноза погоды",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedTextField(
                        value = weatherCityInput,
                        onValueChange = { weatherCityInput = it },
                        label = { Text("Город (для команды «Какая погода?»)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weather_city_input")
                    )
                    OutlinedButton(
                        onClick = { onSaveWeatherCity(weatherCityInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("save_weather_city_button")
                    ) {
                        Text("Сохранить город")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Работа в фоне на Huawei / Honor",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "1) В настройках батареи выберите «ГолосГид» → «Управление вручную» (Разрешить работу в фоне).\n2) На Honor (Magic UI) также откройте «О приложении → Права» и включите «Всплывающие окна в фоне» / «Отображение поверх других окон».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("open_battery_settings_button")
                    ) {
                        Text("Открыть настройки батареи")
                    }
                    OutlinedButton(
                        onClick = onOpenAppDetailsSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("open_app_details_button")
                    ) {
                        Text("О приложении (Права Honor / Huawei)")
                    }
                }
            }
        }

        item {
            Text(
                text = "Параметры голоса и поведения",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingSwitchRow(
                        title = "Только чёткие команды (без шума)",
                        subtitle = "Слушает все голосовые команды и игнорирует постороннюю речь вокруг и звук телевизора.",
                        checked = uiState.settings.strictCommandsOnly,
                        testTagName = "switch_strict_commands",
                        onCheckedChange = onToggleStrictCommands
                    )
                    SettingSwitchRow(
                        title = "Озвучивать действия голосом (TTS)",
                        subtitle = "Помогает незрячему человеку слышать подтверждения",
                        checked = uiState.settings.speakConfirmations,
                        testTagName = "switch_speak_confirmations",
                        onCheckedChange = onToggleSpeakConfirmations
                    )
                    SettingSwitchRow(
                        title = "Говори проще (короткие ответы)",
                        subtitle = "Включается также голосовой командой «Говори проще»",
                        checked = uiState.settings.simpleSpeechMode,
                        testTagName = "switch_simple_speech",
                        onCheckedChange = onToggleSimpleSpeech
                    )
                    SettingSwitchRow(
                        title = "Возвращаться в ГолосГид после «Закрой»",
                        subtitle = "Выключает YouTube и открывает экран с крупными кнопками",
                        checked = uiState.settings.returnToAppOnClose,
                        testTagName = "switch_return_to_app",
                        onCheckedChange = onToggleReturnToApp
                    )
                    SettingSwitchRow(
                        title = "Требовать слово «Слушай» перед командой",
                        subtitle = "Если выключено, команда «Закрой» сработает сразу одним словом",
                        checked = uiState.settings.wakeWordEnabled,
                        testTagName = "switch_wake_word",
                        onCheckedChange = onToggleWakeWord
                    )
                    SettingSwitchRow(
                        title = "Автоматически открывать первое видео",
                        subtitle = "Через 2 сек. после поиска нажимает на первый ролик (нужны Спецвозможности)",
                        checked = uiState.settings.autoOpenFirstVideo,
                        testTagName = "switch_auto_open_first",
                        onCheckedChange = onToggleAutoOpenFirst
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val pct = (uiState.settings.ttsSpeechRate * 100).roundToInt()
                        Text(
                            text = "Скорость озвучки: $pct%",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Slider(
                            value = uiState.settings.ttsSpeechRate,
                            onValueChange = onSpeechRateChange,
                            valueRange = 0.65f..1.5f,
                            modifier = Modifier.testTag("tts_rate_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoskOfflineModelCard(
    modelState: ModelInstallState,
    systemSpeechAvailable: Boolean,
    onDownload: () -> Unit,
    onPickZip: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.5.dp,
            if (modelState is ModelInstallState.Ready) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "9. Офлайн-модель русской речи (Vosk)",
                style = MaterialTheme.typography.titleMedium
            )

            when (modelState) {
                is ModelInstallState.Ready -> {
                    Text(
                        text = "Модель установлена! Распознавание русской речи работает локально без сервисов Google и без интернета.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("delete_vosk_model_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Удалить модель с планшета")
                    }
                }

                is ModelInstallState.Downloading -> {
                    Text(
                        text = "Скачивание модели: ${modelState.progressPercent}% (${"%.1f".format(modelState.downloadedMb)} / ${"%.1f".format(modelState.totalMb)} МБ)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { modelState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is ModelInstallState.Extracting -> {
                    Text(
                        text = modelState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is ModelInstallState.Error, ModelInstallState.NotInstalled -> {
                    val desc = if (modelState is ModelInstallState.Error) {
                        modelState.message
                    } else if (systemSpeechAvailable) {
                        "На устройстве найдена системная служба речи, но для стабильной работы в фоне на Huawei лучше установить офлайн-модель Vosk (~45 МБ)."
                    } else {
                        "Так как на планшете Huawei нет Google-сервисов, скачайте бесплатную офлайн-модель русского языка (~45 МБ) один раз."
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (modelState is ModelInstallState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("download_vosk_model_button")
                    ) {
                        Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Скачать русскую модель (~45 МБ)")
                    }
                    OutlinedButton(
                        onClick = onPickZip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("import_vosk_zip_button")
                    ) {
                        Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Выбрать готовый ZIP-файл с планшета")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTagName: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTagName)
        )
    }
}
