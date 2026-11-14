package com.example.voice

/**
 * Единый расширяемый словарь голосовых команд (intent_id -> синонимичные фразы -> действие).
 * Пользователь не обязан запоминать точную формулировку — все синонимы ведут к одному действию.
 */
data class VoiceCommandEntry(
    val intentId: String,
    val canonicalAction: String,
    val category: String,
    val phrases: List<String>,
    val requiredPermissions: List<String> = emptyList(),
    val confirmationRequired: Boolean = false,
    val spokenResponse: String,
    val sampleTestPhrase: String = phrases.first(),
    val buildCommand: () -> VoiceCommand
)

object VoiceCommandCatalog {

    val entries: List<VoiceCommandEntry> = listOf(
        // --- 1. Фонарик ---
        VoiceCommandEntry(
            intentId = "FLASHLIGHT_OFF",
            canonicalAction = "Выключить фонарик",
            category = "1. Фонарик",
            phrases = listOf(
                "выключи фонарик", "фонарик выключи", "выключи свет", "погаси свет",
                "погаси фонарик", "отключи вспышку", "отключи фонарик", "выключить фонарик"
            ),
            spokenResponse = "Выключаю фонарик",
            buildCommand = { VoiceCommand.SetFlashlight(false) }
        ),
        VoiceCommandEntry(
            intentId = "FLASHLIGHT_CHECK",
            canonicalAction = "Проверить состояние фонарика",
            category = "1. Фонарик",
            phrases = listOf("фонарик включен", "свет включен", "проверь фонарик", "горит ли фонарик"),
            spokenResponse = "Проверяю фонарик",
            buildCommand = { VoiceCommand.CheckFlashlight }
        ),
        VoiceCommandEntry(
            intentId = "FLASHLIGHT_TOGGLE",
            canonicalAction = "Переключить фонарик",
            category = "1. Фонарик",
            phrases = listOf("переключи фонарик", "измени состояние фонарика"),
            spokenResponse = "Переключаю фонарик",
            buildCommand = { VoiceCommand.ToggleFlashlight }
        ),
        VoiceCommandEntry(
            intentId = "FLASHLIGHT_ON",
            canonicalAction = "Включить фонарик",
            category = "1. Фонарик",
            phrases = listOf(
                "включи фонарик", "фонарик включи", "включи свет", "дай свет",
                "зажги фонарик", "мне нужен свет", "включи вспышку", "включить фонарик"
            ),
            spokenResponse = "Включаю фонарик",
            buildCommand = { VoiceCommand.SetFlashlight(true) }
        ),

        // --- 2. Wi-Fi и интернет ---
        VoiceCommandEntry(
            intentId = "WIFI_SETTINGS",
            canonicalAction = "Открыть настройки Wi-Fi",
            category = "2. Wi-Fi и интернет",
            phrases = listOf(
                "открой настройки вайфая", "открой настройки вай фая", "открой беспроводные сети",
                "покажи доступные сети", "настройки вайфая", "настройки вай фай"
            ),
            spokenResponse = "Открываю настройки Wi-Fi",
            buildCommand = { VoiceCommand.OpenWifiSettings }
        ),
        VoiceCommandEntry(
            intentId = "WIFI_CHECK",
            canonicalAction = "Проверить Wi-Fi и интернет",
            category = "2. Wi-Fi и интернет",
            phrases = listOf(
                "вайфай включен", "вай фай включен", "есть интернет", "подключен ли интернет",
                "проверь интернет", "проверь вайфай", "работает ли интернет"
            ),
            spokenResponse = "Проверяю подключение к интернету",
            buildCommand = { VoiceCommand.CheckWifiAndInternet }
        ),
        VoiceCommandEntry(
            intentId = "WIFI_OFF",
            canonicalAction = "Выключить Wi-Fi",
            category = "2. Wi-Fi и интернет",
            phrases = listOf(
                "выключи вай фай", "выключи вайфай", "отключи вайфай", "отключи вай фай",
                "выключи беспроводную сеть", "отключи беспроводной интернет"
            ),
            spokenResponse = "Выключаю Wi-Fi или открываю панель настроек",
            buildCommand = { VoiceCommand.SetWifi(false) }
        ),
        VoiceCommandEntry(
            intentId = "WIFI_ON",
            canonicalAction = "Включить Wi-Fi",
            category = "2. Wi-Fi и интернет",
            phrases = listOf(
                "включи вай фай", "включи вайфай", "подключи вай фай", "подключи вайфай",
                "активируй беспроводную сеть", "включи беспроводной интернет"
            ),
            spokenResponse = "Включаю Wi-Fi или открываю панель настроек",
            buildCommand = { VoiceCommand.SetWifi(true) }
        ),

        // --- 3. Bluetooth ---
        VoiceCommandEntry(
            intentId = "BLUETOOTH_SETTINGS",
            canonicalAction = "Открыть настройки Bluetooth",
            category = "3. Bluetooth",
            phrases = listOf(
                "открой настройки блютуза", "открой настройки блютуз", "найди устройства",
                "подключи наушники", "настройки блютуза"
            ),
            spokenResponse = "Открываю настройки Bluetooth",
            buildCommand = { VoiceCommand.OpenBluetoothSettings }
        ),
        VoiceCommandEntry(
            intentId = "BLUETOOTH_CHECK",
            canonicalAction = "Проверить Bluetooth и наушники",
            category = "3. Bluetooth",
            phrases = listOf(
                "блютуз включен", "bluetooth включен", "подключены ли наушники",
                "проверь блютуз", "наушники подключены"
            ),
            spokenResponse = "Проверяю состояние Bluetooth",
            buildCommand = { VoiceCommand.CheckBluetooth }
        ),
        VoiceCommandEntry(
            intentId = "BLUETOOTH_OFF",
            canonicalAction = "Выключить Bluetooth",
            category = "3. Bluetooth",
            phrases = listOf(
                "выключи блютуз", "отключи bluetooth", "отключи блютуз", "деактивируй блютуз"
            ),
            requiredPermissions = listOf("BLUETOOTH_CONNECT"),
            spokenResponse = "Выключаю Bluetooth",
            buildCommand = { VoiceCommand.SetBluetooth(false) }
        ),
        VoiceCommandEntry(
            intentId = "BLUETOOTH_ON",
            canonicalAction = "Включить Bluetooth",
            category = "3. Bluetooth",
            phrases = listOf(
                "включи блютуз", "включи bluetooth", "активируй блютуз", "подключи блютуз"
            ),
            requiredPermissions = listOf("BLUETOOTH_CONNECT"),
            spokenResponse = "Включаю Bluetooth",
            buildCommand = { VoiceCommand.SetBluetooth(true) }
        ),

        // --- 4. Яркость экрана ---
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_MAX",
            canonicalAction = "Максимальная яркость экрана",
            category = "4. Яркость экрана",
            phrases = listOf(
                "максимальная яркость", "яркость на максимум", "сделай экран максимально ярким",
                "яркость максимум"
            ),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Устанавливаю максимальную яркость",
            buildCommand = { VoiceCommand.SetBrightnessExtreme(max = true) }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_MIN",
            canonicalAction = "Минимальная яркость экрана",
            category = "4. Яркость экрана",
            phrases = listOf(
                "минимальная яркость", "яркость на минимум", "яркость минимум",
                "сделай экран максимально темным"
            ),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Устанавливаю минимальную яркость",
            buildCommand = { VoiceCommand.SetBrightnessExtreme(max = false) }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_AUTO_OFF",
            canonicalAction = "Отключить автояркость",
            category = "4. Яркость экрана",
            phrases = listOf("отключи автояркость", "выключи автояркость", "выключи автоматическую яркость"),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Отключаю автояркость",
            buildCommand = { VoiceCommand.SetAutoBrightness(false) }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_AUTO_ON",
            canonicalAction = "Включить автояркость",
            category = "4. Яркость экрана",
            phrases = listOf("автоматическая яркость", "включи автояркость", "включи автоматическую яркость"),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Включаю автоматическую яркость",
            buildCommand = { VoiceCommand.SetAutoBrightness(true) }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_CHECK",
            canonicalAction = "Проверить яркость экрана",
            category = "4. Яркость экрана",
            phrases = listOf("какая яркость экрана", "какая яркость", "проверь яркость"),
            spokenResponse = "Озвучиваю текущую яркость экрана",
            buildCommand = { VoiceCommand.CheckBrightness }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_UP",
            canonicalAction = "Увеличить яркость экрана",
            category = "4. Яркость экрана",
            phrases = listOf(
                "сделай экран ярче", "сделай ярче", "увеличь яркость", "прибавь яркость",
                "экран посветлее", "добавь света", "экран ярче"
            ),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Увеличиваю яркость экрана",
            buildCommand = { VoiceCommand.AdjustBrightness(brighter = true) }
        ),
        VoiceCommandEntry(
            intentId = "BRIGHTNESS_DOWN",
            canonicalAction = "Уменьшить яркость экрана",
            category = "4. Яркость экрана",
            phrases = listOf(
                "сделай экран темнее", "сделай темнее", "уменьши яркость", "убавь яркость",
                "экран потемнее", "экран темнее"
            ),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Уменьшаю яркость экрана",
            buildCommand = { VoiceCommand.AdjustBrightness(brighter = false) }
        ),

        // --- 5. Громкость и озвучка ---
        VoiceCommandEntry(
            intentId = "VOLUME_MAX",
            canonicalAction = "Максимальная громкость",
            category = "5. Громкость и звук",
            phrases = listOf("максимальная громкость", "звук на максимум", "громкость на максимум"),
            spokenResponse = "Устанавливаю максимальную громкость",
            buildCommand = { VoiceCommand.VolumeMax }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_MIN",
            canonicalAction = "Выключить звук / минимум",
            category = "5. Громкость и звук",
            phrases = listOf(
                "минимальная громкость", "выключи звук", "отключи звук",
                "без звука", "звук выключи", "звук на минимум"
            ),
            spokenResponse = "Выключаю звук",
            buildCommand = { VoiceCommand.VolumeMute }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_RING_UP",
            canonicalAction = "Увеличить громкость звонка",
            category = "5. Громкость и звук",
            phrases = listOf(
                "громче звонок", "увеличь громкость звонка", "прибавь громкость звонка",
                "громкость звонка больше", "звонок громче"
            ),
            spokenResponse = "Увеличиваю громкость звонка",
            buildCommand = { VoiceCommand.RingVolumeUp }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_RING_DOWN",
            canonicalAction = "Уменьшить громкость звонка",
            category = "5. Громкость и звук",
            phrases = listOf("тише звонок", "уменьши громкость звонка", "убавь громкость звонка", "звонок тише"),
            spokenResponse = "Уменьшаю громкость звонка",
            buildCommand = { VoiceCommand.RingVolumeDown }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_MEDIA_UP",
            canonicalAction = "Громче видео / музыку",
            category = "5. Громкость и звук",
            phrases = listOf("громче видео", "увеличь звук музыки", "прибавь звук видео", "музыка громче"),
            spokenResponse = "Делаю видео и музыку громче",
            buildCommand = { VoiceCommand.MediaVolumeUp }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_MEDIA_DOWN",
            canonicalAction = "Тише видео / музыку",
            category = "5. Громкость и звук",
            phrases = listOf("тише видео", "уменьши звук музыки", "убавь звук видео", "музыка тише"),
            spokenResponse = "Делаю видео и музыку тише",
            buildCommand = { VoiceCommand.MediaVolumeDown }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_CHECK",
            canonicalAction = "Проверить уровень громкости",
            category = "5. Громкость и звук",
            phrases = listOf("какая громкость", "сколько сейчас громкости", "проверь громкость"),
            spokenResponse = "Озвучиваю текущий уровень громкости",
            buildCommand = { VoiceCommand.CheckVolume }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_LOUDER",
            canonicalAction = "Говорить громче (озвучка)",
            category = "5. Громкость и звук",
            phrases = listOf("говори громче", "скажи громче", "отвечай громче"),
            spokenResponse = "Увеличиваю громкость голоса помощника",
            buildCommand = { VoiceCommand.SpeakLouder }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_QUIETER",
            canonicalAction = "Говорить тише (озвучка)",
            category = "5. Громкость и звук",
            phrases = listOf("говори тише", "отвечай тише"),
            spokenResponse = "Уменьшаю громкость голоса помощника",
            buildCommand = { VoiceCommand.SpeakQuieter }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_SLOWER",
            canonicalAction = "Говорить медленнее",
            category = "5. Громкость и звук",
            phrases = listOf("говори медленнее", "медленнее говори", "говори помедленнее"),
            spokenResponse = "Замедляю речь помощника",
            buildCommand = { VoiceCommand.SpeakSlower }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_FASTER",
            canonicalAction = "Говорить быстрее",
            category = "5. Громкость и звук",
            phrases = listOf("говори быстрее", "ускорь речь"),
            spokenResponse = "Ускоряю речь помощника",
            buildCommand = { VoiceCommand.SpeakFaster }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_SIMPLER",
            canonicalAction = "Говорить проще",
            category = "5. Громкость и звук",
            phrases = listOf("говори проще", "отвечай проще", "объясни проще", "скажи понятнее", "короче говори"),
            spokenResponse = "Включаю короткие и простые ответы",
            buildCommand = { VoiceCommand.SpeakSimpler }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_UP",
            canonicalAction = "Увеличить общую громкость",
            category = "5. Громкость и звук",
            phrases = listOf(
                "громче", "сделай громче", "увеличь громкость", "прибавь звук",
                "добавь громкости", "сделай погромче", "прибавь громкость", "звук громче"
            ),
            spokenResponse = "Увеличиваю громкость",
            buildCommand = { VoiceCommand.VolumeUp }
        ),
        VoiceCommandEntry(
            intentId = "VOLUME_DOWN",
            canonicalAction = "Уменьшить общую громкость",
            category = "5. Громкость и звук",
            phrases = listOf(
                "тише", "сделай тише", "уменьши громкость", "убавь звук",
                "сделай потише", "звук тише"
            ),
            spokenResponse = "Уменьшаю громкость",
            buildCommand = { VoiceCommand.VolumeDown }
        ),

        // --- 6. Телефонные звонки ---
        VoiceCommandEntry(
            intentId = "CALL_LAST",
            canonicalAction = "Позвонить последнему абоненту",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "перезвони последнему", "позвони последнему", "позвонить последнему",
                "набери последнему", "набери последний номер"
            ),
            requiredPermissions = listOf("READ_CALL_LOG", "CALL_PHONE"),
            confirmationRequired = true,
            spokenResponse = "Нахожу последний номер и спрашиваю подтверждение",
            buildCommand = { VoiceCommand.CallLastContact }
        ),
        VoiceCommandEntry(
            intentId = "CALL_ANSWER",
            canonicalAction = "Ответить на входящий звонок",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "ответь", "ответь на звонок", "ответить на звонок",
                "принять вызов", "прими звонок", "подними трубку", "возьми трубку"
            ),
            requiredPermissions = listOf("ANSWER_PHONE_CALLS"),
            spokenResponse = "Отвечаю на звонок",
            buildCommand = { VoiceCommand.AnswerCall }
        ),
        VoiceCommandEntry(
            intentId = "CALL_REJECT",
            canonicalAction = "Отклонить входящий звонок",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "сбрось", "отклони звонок", "отклонить звонок", "отклони вызов", "не отвечай", "сбрось звонок"
            ),
            requiredPermissions = listOf("ANSWER_PHONE_CALLS"),
            spokenResponse = "Отклоняю звонок",
            buildCommand = { VoiceCommand.RejectCall }
        ),
        VoiceCommandEntry(
            intentId = "CALL_END",
            canonicalAction = "Завершить разговор",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "положи трубку", "закончи разговор", "заверши звонок", "отключись"
            ),
            requiredPermissions = listOf("ANSWER_PHONE_CALLS"),
            spokenResponse = "Завершаю звонок",
            buildCommand = { VoiceCommand.EndCall }
        ),
        VoiceCommandEntry(
            intentId = "SPEAKERPHONE_OFF",
            canonicalAction = "Выключить громкую связь",
            category = "6. Телефонные звонки",
            phrases = listOf("выключи громкую связь", "отключи громкую связь", "отключи динамик"),
            spokenResponse = "Выключаю громкую связь",
            buildCommand = { VoiceCommand.DisableSpeakerphone }
        ),
        VoiceCommandEntry(
            intentId = "SPEAKERPHONE_ON",
            canonicalAction = "Включить громкую связь",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "включи громкую связь", "включить громкую связь", "громкая связь",
                "говори через динамик", "включи динамик"
            ),
            spokenResponse = "Включаю громкую связь",
            buildCommand = { VoiceCommand.EnableSpeakerphone }
        ),
        VoiceCommandEntry(
            intentId = "CALL_MISSED",
            canonicalAction = "Прочитать пропущенные звонки",
            category = "6. Телефонные звонки",
            phrases = listOf(
                "кто мне звонил", "покажи пропущенные", "покажи пропущенные звонки",
                "прочитай пропущенные звонки", "пропущенные звонки"
            ),
            requiredPermissions = listOf("READ_CALL_LOG"),
            spokenResponse = "Озвучиваю пропущенные звонки",
            buildCommand = { VoiceCommand.ShowMissedCalls }
        ),

        // --- 7. WhatsApp и сообщения ---
        VoiceCommandEntry(
            intentId = "WHATSAPP_CLOSE",
            canonicalAction = "Закрыть WhatsApp",
            category = "7. WhatsApp и сообщения",
            phrases = listOf("закрой ватсап", "закрой вацап", "выйди из ватсапа", "выйди из вацапа"),
            spokenResponse = "Выхожу из WhatsApp",
            buildCommand = { VoiceCommand.CloseWhatsApp }
        ),
        VoiceCommandEntry(
            intentId = "WHATSAPP_OPEN",
            canonicalAction = "Открыть WhatsApp",
            category = "7. WhatsApp и сообщения",
            phrases = listOf(
                "открой ватсап", "открой вацап", "открой whatsapp", "запусти ватсап",
                "запусти вацап", "зайди в ватсап", "зайди в вацап", "открой вотсап"
            ),
            spokenResponse = "Открываю WhatsApp",
            buildCommand = { VoiceCommand.OpenWhatsApp }
        ),
        VoiceCommandEntry(
            intentId = "MESSAGES_READ",
            canonicalAction = "Прочитать новые сообщения и SMS",
            category = "7. WhatsApp и сообщения",
            phrases = listOf(
                "прочитай сообщения", "прочитать сообщения", "прочитай ватсап", "прочитай вацап",
                "есть новые сообщения", "прочитай смс", "кто написал", "новые сообщения", "какие сообщения"
            ),
            requiredPermissions = listOf("BIND_NOTIFICATION_LISTENER_SERVICE"),
            spokenResponse = "Озвучиваю входящие сообщения",
            buildCommand = { VoiceCommand.ReadMessages }
        ),

        // --- 9. Управление YouTube и видео ---
        VoiceCommandEntry(
            intentId = "VIDEO_FULLSCREEN_EXIT",
            canonicalAction = "Выйти из полноэкранного режима",
            category = "9. Управление YouTube и видео",
            phrases = listOf(
                "выйти из полного экрана", "выйди из полного экрана", "выход из полного экрана",
                "обычный режим", "сверни видео"
            ),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Выхожу из полноэкранного режима",
            buildCommand = { VoiceCommand.ExitFullscreen }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_FULLSCREEN_ENTER",
            canonicalAction = "Включить полноэкранный режим",
            category = "9. Управление YouTube и видео",
            phrases = listOf("на весь экран", "полный экран", "полноэкранный режим", "разверни видео"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Разворачиваю видео на весь экран",
            buildCommand = { VoiceCommand.EnterFullscreen }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_STOP",
            canonicalAction = "Выключить видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf("выключи видео", "останови воспроизведение", "выключи воспроизведение"),
            spokenResponse = "Останавливаю воспроизведение",
            buildCommand = { VoiceCommand.StopVideo }
        ),
        VoiceCommandEntry(
            intentId = "YOUTUBE_SUBSCRIPTIONS",
            canonicalAction = "Открыть подписки на YouTube",
            category = "9. Управление YouTube и видео",
            phrases = listOf("открой подписки", "открыть подписки", "подписки ютуб", "мои подписки"),
            spokenResponse = "Открываю ваши подписки на YouTube",
            buildCommand = { VoiceCommand.OpenYouTubeSubscriptions }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_PAUSE",
            canonicalAction = "Поставить видео на паузу",
            category = "9. Управление YouTube и видео",
            phrases = listOf("пауза", "останови видео", "приостанови", "поставь на паузу", "на паузу", "поставь паузу"),
            spokenResponse = "Ставлю видео на паузу",
            buildCommand = { VoiceCommand.PauseVideo }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_RESUME",
            canonicalAction = "Продолжить видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf(
                "продолжи", "продолжить", "продолжай", "запускай", "включи видео",
                "воспроизводи", "играй", "сними с паузы", "возобнови"
            ),
            spokenResponse = "Продолжаю воспроизведение",
            buildCommand = { VoiceCommand.ResumeVideo }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_NEXT",
            canonicalAction = "Следующее видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf("следующее видео", "включи следующее", "следующий ролик"),
            spokenResponse = "Переключаю на следующее видео",
            buildCommand = { VoiceCommand.MediaNext }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_PREVIOUS",
            canonicalAction = "Предыдущее видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf("предыдущее видео", "верни прошлое", "предыдущий ролик", "предыдущее", "прошлое видео"),
            spokenResponse = "Возвращаю предыдущее видео",
            buildCommand = { VoiceCommand.MediaPrevious }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_SEEK_FORWARD",
            canonicalAction = "Перемотать вперёд на 10 секунд",
            category = "9. Управление YouTube и видео",
            phrases = listOf(
                "перемотай вперед", "вперед на десять секунд", "вперед на 10 секунд",
                "промотай дальше", "перемотать вперед", "мотай вперед"
            ),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Перематываю на 10 секунд вперёд",
            buildCommand = { VoiceCommand.SeekForward }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_SEEK_BACKWARD",
            canonicalAction = "Перемотать назад на 10 секунд",
            category = "9. Управление YouTube и видео",
            phrases = listOf(
                "назад на десять секунд", "назад на 10 секунд", "перемотай назад",
                "повтори момент", "перемотать назад", "мотай назад"
            ),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Перематываю на 10 секунд назад",
            buildCommand = { VoiceCommand.SeekBackward }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_RESTART",
            canonicalAction = "Начать видео сначала",
            category = "9. Управление YouTube и видео",
            phrases = listOf("сначала", "начни заново", "перезапусти видео", "начни сначала", "видео сначала"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Запускаю видео сначала",
            buildCommand = { VoiceCommand.RestartVideo }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_SPEED_UP",
            canonicalAction = "Увеличить скорость видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf("скорость больше", "больше скорость", "увеличь скорость", "ускорь видео"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Увеличиваю скорость видео",
            buildCommand = { VoiceCommand.SpeedUp }
        ),
        VoiceCommandEntry(
            intentId = "VIDEO_SPEED_DOWN",
            canonicalAction = "Уменьшить скорость видео",
            category = "9. Управление YouTube и видео",
            phrases = listOf("скорость меньше", "меньше скорость", "уменьши скорость", "замедли видео"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Уменьшаю скорость видео",
            buildCommand = { VoiceCommand.SpeedDown }
        ),

        // --- 10. Чтение экрана и помощь незрячим ---
        VoiceCommandEntry(
            intentId = "SCREEN_READ_CONTINUE",
            canonicalAction = "Прокрутить и читать дальше",
            category = "10. Чтение экрана и уведомления",
            phrases = listOf("продолжай читать", "читай дальше"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Прокручиваю экран и читаю дальше",
            buildCommand = { VoiceCommand.ContinueReadingScreen }
        ),
        VoiceCommandEntry(
            intentId = "SCREEN_READ",
            canonicalAction = "Прочитать содержимое экрана",
            category = "10. Чтение экрана и уведомления",
            phrases = listOf(
                "прочитай экран", "озвучь экран", "что написано на экране",
                "прочитай мне", "что на экране", "читай экран"
            ),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Читаю текст на экране",
            buildCommand = { VoiceCommand.ReadScreen }
        ),
        VoiceCommandEntry(
            intentId = "SPEECH_STOP",
            canonicalAction = "Остановить озвучивание",
            category = "10. Чтение экрана и уведомления",
            phrases = listOf("замолчи", "перестань говорить", "останови озвучку"),
            spokenResponse = "Озвучка остановлена",
            buildCommand = { VoiceCommand.StopSpeaking }
        ),
        VoiceCommandEntry(
            intentId = "NOTIFICATIONS_READ",
            canonicalAction = "Прочитать пропущенные уведомления",
            category = "10. Чтение экрана и уведомления",
            phrases = listOf(
                "прочитай уведомления", "что нового", "какие уведомления",
                "что я пропустил", "что я пропустила", "пропущенные уведомления"
            ),
            requiredPermissions = listOf("BIND_NOTIFICATION_LISTENER_SERVICE"),
            spokenResponse = "Читаю последние уведомления",
            buildCommand = { VoiceCommand.ReadMissedNotifications }
        ),
        VoiceCommandEntry(
            intentId = "CURRENT_APP_CHECK",
            canonicalAction = "Узнать, какое приложение открыто",
            category = "10. Чтение экрана и уведомления",
            phrases = listOf("какое приложение открыто", "где я нахожусь", "какое сейчас приложение"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Сообщаю название открытого приложения",
            buildCommand = { VoiceCommand.ReadCurrentApp }
        ),

        // --- 11. Системные функции Android ---
        VoiceCommandEntry(
            intentId = "SYSTEM_LOCK",
            canonicalAction = "Заблокировать экран",
            category = "11. Системные функции",
            phrases = listOf("заблокируй экран", "выключи экран", "заблокируй телефон", "заблокируй планшет"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Блокирую экран",
            buildCommand = { VoiceCommand.LockScreen }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_HOME",
            canonicalAction = "На главный экран",
            category = "11. Системные функции",
            phrases = listOf("на главный экран", "вернись домой", "домой", "главный экран", "рабочий стол"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Перехожу на главный экран",
            buildCommand = { VoiceCommand.GoHome }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_BACK",
            canonicalAction = "Назад (предыдущий экран)",
            category = "11. Системные функции",
            phrases = listOf("назад", "вернись", "вернись назад", "предыдущий экран"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Возвращаюсь назад",
            buildCommand = { VoiceCommand.GoBack }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_RECENTS",
            canonicalAction = "Недавние (открытые) приложения",
            category = "11. Системные функции",
            phrases = listOf("последние приложения", "покажи открытые приложения", "недавние приложения"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Открываю список запущенных приложений",
            buildCommand = { VoiceCommand.OpenRecents }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_SCREENSHOT",
            canonicalAction = "Сделать скриншот",
            category = "11. Системные функции",
            phrases = listOf("сделай скриншот", "снимок экрана", "скриншот"),
            requiredPermissions = listOf("BIND_ACCESSIBILITY_SERVICE"),
            spokenResponse = "Делаю снимок экрана",
            buildCommand = { VoiceCommand.TakeScreenshot }
        ),
        VoiceCommandEntry(
            intentId = "ROTATE_ON",
            canonicalAction = "Включить автоповорот экрана",
            category = "11. Системные функции",
            phrases = listOf("включи автоповорот", "включи поворот экрана"),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Включаю автоповорот",
            buildCommand = { VoiceCommand.SetAutoRotate(true) }
        ),
        VoiceCommandEntry(
            intentId = "ROTATE_OFF",
            canonicalAction = "Отключить автоповорот экрана",
            category = "11. Системные функции",
            phrases = listOf("отключи автоповорот", "выключи автоповорот", "отключи поворот экрана"),
            requiredPermissions = listOf("WRITE_SETTINGS"),
            spokenResponse = "Отключаю автоповорот",
            buildCommand = { VoiceCommand.SetAutoRotate(false) }
        ),
        VoiceCommandEntry(
            intentId = "DND_ON",
            canonicalAction = "Включить режим «Не беспокоить»",
            category = "11. Системные функции",
            phrases = listOf("включи не беспокоить", "режим не беспокоить"),
            spokenResponse = "Включаю режим Не беспокоить",
            buildCommand = { VoiceCommand.SetDoNotDisturb(true) }
        ),
        VoiceCommandEntry(
            intentId = "DND_OFF",
            canonicalAction = "Отключить режим «Не беспокоить»",
            category = "11. Системные функции",
            phrases = listOf("отключи не беспокоить", "выключи не беспокоить"),
            spokenResponse = "Отключаю режим Не беспокоить",
            buildCommand = { VoiceCommand.SetDoNotDisturb(false) }
        ),
        VoiceCommandEntry(
            intentId = "LOCATION_SETTINGS",
            canonicalAction = "Открыть настройки геолокации",
            category = "11. Системные функции",
            phrases = listOf("включи геолокацию", "отключи геолокацию", "настройки геолокации"),
            spokenResponse = "Открываю настройки геолокации",
            buildCommand = { VoiceCommand.OpenLocationSettings }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_TIME",
            canonicalAction = "Озвучить текущее время",
            category = "11. Системные функции",
            phrases = listOf("который час", "сколько времени", "подскажи время", "скажи время"),
            spokenResponse = "Озвучиваю текущее время",
            buildCommand = { VoiceCommand.ReadTime }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_DATE",
            canonicalAction = "Озвучить сегодняшнюю дату",
            category = "11. Системные функции",
            phrases = listOf("какое сегодня число", "какая сегодня дата", "какое число", "сегодня число", "какая дата"),
            spokenResponse = "Озвучиваю сегодняшнее число",
            buildCommand = { VoiceCommand.ReadDate }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_DAY_OF_WEEK",
            canonicalAction = "Озвучить день недели",
            category = "11. Системные функции",
            phrases = listOf("какой сегодня день недели", "какой день недели", "какой сегодня день", "день недели"),
            spokenResponse = "Озвучиваю день недели",
            buildCommand = { VoiceCommand.ReadDayOfWeek }
        ),
        VoiceCommandEntry(
            intentId = "SYSTEM_BATTERY",
            canonicalAction = "Проверить уровень заряда",
            category = "11. Системные функции",
            phrases = listOf(
                "сколько зарядки", "сколько осталось зарядки", "сколько процентов батареи",
                "проверь аккумулятор", "покажи зарядку", "сколько заряда", "уровень заряда"
            ),
            spokenResponse = "Озвучиваю процент заряда батареи",
            buildCommand = { VoiceCommand.ReadBattery }
        ),

        // --- 12. Будильники и напоминания ---
        VoiceCommandEntry(
            intentId = "ALARM_OFF",
            canonicalAction = "Отключить / открыть будильники",
            category = "12. Будильники и напоминания",
            phrases = listOf("выключи будильник", "отключи будильник", "удали будильник"),
            spokenResponse = "Отключаю будильник или открываю список будильников",
            buildCommand = { VoiceCommand.DisableAlarm }
        ),
        VoiceCommandEntry(
            intentId = "ALARM_SNOOZE",
            canonicalAction = "Отложить будильник",
            category = "12. Будильники и напоминания",
            phrases = listOf("еще пять минут", "отложи будильник", "дай поспать"),
            spokenResponse = "Откладываю будильник",
            buildCommand = { VoiceCommand.SnoozeAlarm }
        ),
        VoiceCommandEntry(
            intentId = "REMINDERS_CHECK",
            canonicalAction = "Прочитать активные напоминания",
            category = "12. Будильники и напоминания",
            phrases = listOf("какие у меня напоминания", "что мне сегодня нужно сделать", "мои напоминания"),
            spokenResponse = "Читаю ваши напоминания",
            buildCommand = { VoiceCommand.ReadReminders }
        ),
        VoiceCommandEntry(
            intentId = "REMINDERS_CANCEL",
            canonicalAction = "Отменить напоминания",
            category = "12. Будильники и напоминания",
            phrases = listOf("отмени напоминание", "отмени напоминания", "удали напоминание"),
            spokenResponse = "Отменяю активные напоминания",
            buildCommand = { VoiceCommand.CancelReminders }
        ),

        // --- 13. Погода и информация ---
        VoiceCommandEntry(
            intentId = "WEATHER_TOMORROW",
            canonicalAction = "Прогноз погоды на завтра",
            category = "13. Погода и информация",
            phrases = listOf("какая погода завтра", "погода на завтра", "какая погода на выходных"),
            spokenResponse = "Запрашиваю прогноз погоды на завтра",
            buildCommand = { VoiceCommand.ReadWeather(tomorrow = true) }
        ),
        VoiceCommandEntry(
            intentId = "WEATHER_PRECIPITATION",
            canonicalAction = "Будет ли дождь или снег",
            category = "13. Погода и информация",
            phrases = listOf("будет дождь", "пойдет дождь", "будет ли дождь", "пойдет снег", "будет снег"),
            spokenResponse = "Проверяю вероятность осадков",
            buildCommand = { VoiceCommand.ReadWeather(askPrecipitation = true) }
        ),
        VoiceCommandEntry(
            intentId = "WEATHER_NOW",
            canonicalAction = "Текущая погода и температура",
            category = "13. Погода и информация",
            phrases = listOf(
                "погода", "какая погода", "погода сейчас", "погода сегодня",
                "погода на сегодня", "прогноз погоды", "что на улице",
                "расскажи про погоду", "сколько градусов",
                "какая температура на улице", "какая сегодня погода",
                "скажи погоду", "скажи какая погода"
            ),
            spokenResponse = "Запрашиваю текущую погоду",
            buildCommand = { VoiceCommand.ReadWeather() }
        ),
        VoiceCommandEntry(
            intentId = "CURRENCY_RATES",
            canonicalAction = "Курсы доллара и евро",
            category = "13. Погода и информация",
            phrases = listOf("сколько стоит доллар", "какой курс евро", "курс доллара", "курс валют"),
            spokenResponse = "Запрашиваю курсы валют ЦБ",
            buildCommand = { VoiceCommand.ReadCurrencyRates }
        ),

        // --- 14. Карты и навигация ---
        VoiceCommandEntry(
            intentId = "MAPS_OPEN",
            canonicalAction = "Открыть карты или навигатор",
            category = "14. Карты и передвижение",
            phrases = listOf("открой карты", "запусти навигатор", "открой навигацию", "открой навигатор"),
            spokenResponse = "Открываю карты",
            buildCommand = { VoiceCommand.OpenMaps }
        ),

        // --- 15. Камера ---
        VoiceCommandEntry(
            intentId = "CAMERA_VIDEO",
            canonicalAction = "Открыть камеру в режиме видео",
            category = "15. Камера",
            phrases = listOf("запиши видео", "начни видеозапись", "включи видеозапись"),
            spokenResponse = "Открываю камеру для записи видео",
            buildCommand = { VoiceCommand.OpenCamera(videoMode = true) }
        ),
        VoiceCommandEntry(
            intentId = "CAMERA_PHOTO",
            canonicalAction = "Открыть камеру / сделать фото",
            category = "15. Камера",
            phrases = listOf("открой камеру", "включи камеру", "включи фотоаппарат", "запусти фотоаппарат", "сделай фото", "сфотографируй"),
            spokenResponse = "Открываю камеру",
            buildCommand = { VoiceCommand.OpenCamera(videoMode = false) }
        ),

        // --- 16. Экстренная помощь ---
        VoiceCommandEntry(
            intentId = "SOS_EMERGENCY_SERVICES",
            canonicalAction = "Экстренный вызов 112 (с подтверждением)",
            category = "16. Экстренная помощь",
            phrases = listOf("экстренный вызов", "вызови экстренную службу", "вызови скорую", "мне нужна помощь врача"),
            requiredPermissions = listOf("CALL_PHONE"),
            confirmationRequired = true,
            spokenResponse = "Спрашиваю подтверждение для набора номера 112",
            buildCommand = { VoiceCommand.CallEmergencyServices }
        ),
        VoiceCommandEntry(
            intentId = "SOS_RELATIVE",
            canonicalAction = "Позвонить родственнику на помощь",
            category = "16. Экстренная помощь",
            phrases = listOf("позови на помощь", "вызови помощь", "срочная помощь", "мне плохо"),
            requiredPermissions = listOf("CALL_PHONE"),
            confirmationRequired = true,
            spokenResponse = "Спрашиваю подтверждение экстренного звонка родственнику",
            buildCommand = { VoiceCommand.CallForHelpSos }
        ),
        VoiceCommandEntry(
            intentId = "HELP_MENU",
            canonicalAction = "Помощь рядом",
            category = "16. Экстренная помощь",
            phrases = listOf("мне нужна помощь", "нужна помощь", "помоги мне"),
            spokenResponse = "Озвучиваю быстрые варианты помощи",
            buildCommand = { VoiceCommand.NeedHelpMenu }
        ),

        // --- 17. Управление помощником ---
        VoiceCommandEntry(
            intentId = "ASSISTANT_PING",
            canonicalAction = "Проверка работы помощника",
            category = "17. Управление помощником",
            phrases = listOf("ты меня слышишь", "ты работаешь", "ты тут"),
            spokenResponse = "Да, я вас слышу и готов к работе",
            buildCommand = { VoiceCommand.AssistantPingCheck }
        ),
        VoiceCommandEntry(
            intentId = "ASSISTANT_STOP",
            canonicalAction = "Остановить фоновую работу помощника",
            category = "17. Управление помощником",
            phrases = listOf("закрой помощника", "выключи помощника", "отключи помощника"),
            spokenResponse = "Останавливаю фоновое прослушивание",
            buildCommand = { VoiceCommand.StopAssistantService }
        ),
        VoiceCommandEntry(
            intentId = "ASSISTANT_REPEAT_COMMAND",
            canonicalAction = "Повторить последнюю команду",
            category = "17. Управление помощником",
            phrases = listOf("повтори последнюю команду", "повторить последнюю команду", "повтори команду"),
            spokenResponse = "Повторяю предыдущую команду",
            buildCommand = { VoiceCommand.RepeatLastCommand }
        ),
        VoiceCommandEntry(
            intentId = "ASSISTANT_REPEAT",
            canonicalAction = "Повторить последний ответ",
            category = "17. Управление помощником",
            phrases = listOf("повтори", "повторить", "повтори ответ", "повтори последнее", "скажи еще раз", "повтори еще раз"),
            spokenResponse = "Повторяю последний ответ",
            buildCommand = { VoiceCommand.RepeatLastResponse }
        ),
        VoiceCommandEntry(
            intentId = "ASSISTANT_EXPLAIN",
            canonicalAction = "Пояснить непонятный ответ",
            category = "17. Управление помощником",
            phrases = listOf("я не понял", "не понял", "я не поняла", "не поняла", "что ты сказал"),
            spokenResponse = "Поясняю проще и медленнее",
            buildCommand = { VoiceCommand.ExplainNotUnderstood }
        ),
        VoiceCommandEntry(
            intentId = "ASSISTANT_CAPABILITIES",
            canonicalAction = "Справка о возможностях",
            category = "17. Управление помощником",
            phrases = listOf("что ты умеешь", "какие команды доступны", "какие команды"),
            spokenResponse = "Рассказываю о доступных командах",
            buildCommand = { VoiceCommand.Help }
        ),
        VoiceCommandEntry(
            intentId = "FIND_MY_PHONE",
            canonicalAction = "Поиск устройства громким сигналом",
            category = "17. Управление помощником",
            phrases = listOf("найди мой телефон", "найти мой телефон", "где мой телефон", "найди телефон", "найди планшет"),
            spokenResponse = "Включаю громкий сигнал поиска устройства",
            buildCommand = { VoiceCommand.FindMyPhone }
        )
    )

    val exactPhraseMap: Map<String, VoiceCommandEntry> by lazy {
        buildMap {
            for (entry in this@VoiceCommandCatalog.entries) {
                for (phrase in entry.phrases) {
                    putIfAbsent(phrase, entry)
                }
            }
        }
    }

    val allPreviewKeywords: List<String> = listOf(
        "ютуб", "ютьюб", "ютюб", "ю туб", "открой", "запусти", "зайди", "перейди",
        "включи", "выключи", "найди", "позвони", "набери", "видеозвонок", "напиши",
        "отправь", "ответь", "закрой", "стоп", "выход", "пауза", "продолж", "громче",
        "тише", "перемотай", "скорость", "фонарик", "вайфай", "вай фай", "блютуз",
        "яркость", "погод", "дожд", "будильник", "таймер", "напомни", "прочитай",
        "уведомлен", "пропустил", "заряд", "который час", "сколько времени",
        "какое число", "день недели", "нажми", "кликни", "скриншот", "заблокируй",
        "помощ", "повтор", "отмен", "вацап", "ватсап", "доллар", "евро", "маршрут",
        "камер", "первый", "второй"
    )
}
