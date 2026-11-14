package com.example

import com.example.control.AppLauncherController
import com.example.control.ContactMatchCandidate
import com.example.control.InstalledAppEntry
import com.example.control.WhatsAppContactCaller
import com.example.voice.RussianVoiceCommandParser
import com.example.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianVoiceCommandParserTest {

    @Test
    fun `parses one-word close youtube commands`() {
        assertEquals(VoiceCommand.CloseYouTube, RussianVoiceCommandParser.parse("Закрой", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CloseYouTube, RussianVoiceCommandParser.parse("стоп", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CloseYouTube, RussianVoiceCommandParser.parse("выход", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CloseYouTube, RussianVoiceCommandParser.parse("хватит", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CloseYouTube, RussianVoiceCommandParser.parse("Закрой ютуб!", strictCommandsOnly = true))
    }

    @Test
    fun `parses open youtube and search in single Russian command`() {
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("аудиокниги детективы"),
            RussianVoiceCommandParser.parse("Открой ютуб и найди аудиокниги детективы", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("классическую музыку"),
            RussianVoiceCommandParser.parse("Найди на ютубе классическую музыку", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("новости сегодня"),
            RussianVoiceCommandParser.parse("Включи в ютубе новости сегодня", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("сказки на ночь"),
            RussianVoiceCommandParser.parse("Ютуб найди сказки на ночь", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("рецепты супов"),
            RussianVoiceCommandParser.parse("найди рецепты супов на ютубе", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("аудиокниги"),
            RussianVoiceCommandParser.parse("так вот открой ю дуб и найди аудиокниги", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.OpenYouTubeSearch("природу"),
            RussianVoiceCommandParser.parse("найди видео про природу", strictCommandsOnly = true)
        )
    }

    @Test
    fun `parses video control group commands`() {
        assertEquals(VoiceCommand.PauseVideo, RussianVoiceCommandParser.parse("Пауза", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ResumeVideo, RussianVoiceCommandParser.parse("Продолжить", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ResumeVideo, RussianVoiceCommandParser.parse("Играй", strictCommandsOnly = true))
        assertEquals(VoiceCommand.MediaNext, RussianVoiceCommandParser.parse("Следующее видео", strictCommandsOnly = true))
        assertEquals(VoiceCommand.MediaPrevious, RussianVoiceCommandParser.parse("Предыдущее видео", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SeekForward, RussianVoiceCommandParser.parse("Перемотай вперёд", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SeekBackward, RussianVoiceCommandParser.parse("Перемотай назад", strictCommandsOnly = true))
        assertEquals(VoiceCommand.RestartVideo, RussianVoiceCommandParser.parse("Сначала", strictCommandsOnly = true))
        assertEquals(VoiceCommand.VolumeUp, RussianVoiceCommandParser.parse("Громче", strictCommandsOnly = true))
        assertEquals(VoiceCommand.VolumeDown, RussianVoiceCommandParser.parse("Тише", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SpeedUp, RussianVoiceCommandParser.parse("скорость больше", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SpeedDown, RussianVoiceCommandParser.parse("скорость меньше", strictCommandsOnly = true))
        assertEquals(VoiceCommand.EnterFullscreen, RussianVoiceCommandParser.parse("На весь экран", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ExitFullscreen, RussianVoiceCommandParser.parse("Выйти из полного экрана", strictCommandsOnly = true))
        assertEquals(VoiceCommand.StopVideo, RussianVoiceCommandParser.parse("Выключи видео", strictCommandsOnly = true))
    }

    @Test
    fun `parses youtube presets subscriptions and back`() {
        assertEquals(VoiceCommand.OpenYouTubeHome, RussianVoiceCommandParser.parse("Открой YouTube", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSearch("новости"), RussianVoiceCommandParser.parse("Найди новости", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSearch("музыка"), RussianVoiceCommandParser.parse("Найди музыку", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSearch("фильмы"), RussianVoiceCommandParser.parse("Найди фильм", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSearch("мультики"), RussianVoiceCommandParser.parse("Найди мультики", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSearch("концерты"), RussianVoiceCommandParser.parse("Найди концерт", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenYouTubeSubscriptions, RussianVoiceCommandParser.parse("Открой подписки", strictCommandsOnly = true))
        assertEquals(VoiceCommand.GoBack, RussianVoiceCommandParser.parse("Вернись назад", strictCommandsOnly = true))
    }

    @Test
    fun `parses phone whatsapp calls video calls digits and message preparation`() {
        assertEquals(VoiceCommand.OpenWhatsApp, RussianVoiceCommandParser.parse("открой вацап", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CloseWhatsApp, RussianVoiceCommandParser.parse("закрой ватсап", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallContact("дочери"), RussianVoiceCommandParser.parse("Позвони дочери.", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallContact("сыну"), RussianVoiceCommandParser.parse("Позвони сыну.", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallContact("внуку"), RussianVoiceCommandParser.parse("Позвони внуку.", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallContact("ивану", preferWhatsApp = true), RussianVoiceCommandParser.parse("набери по вацапу ивану", strictCommandsOnly = true))
        assertEquals(VoiceCommand.VideoCallContact("сыну"), RussianVoiceCommandParser.parse("Видеозвонок сыну", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallPhoneNumber("89001234567"), RussianVoiceCommandParser.parse("Позвони на номер 8 900 123 45 67", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallLastContact, RussianVoiceCommandParser.parse("Позвони последнему", strictCommandsOnly = true))
        assertEquals(VoiceCommand.AnswerCall, RussianVoiceCommandParser.parse("Ответь на звонок", strictCommandsOnly = true))
        assertEquals(VoiceCommand.RejectCall, RussianVoiceCommandParser.parse("Отклони звонок", strictCommandsOnly = true))
        assertEquals(VoiceCommand.EndCall, RussianVoiceCommandParser.parse("Положи трубку", strictCommandsOnly = true))
        assertEquals(VoiceCommand.EnableSpeakerphone, RussianVoiceCommandParser.parse("Включи громкую связь", strictCommandsOnly = true))
        assertEquals(VoiceCommand.DisableSpeakerphone, RussianVoiceCommandParser.parse("Выключи громкую связь", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ShowMissedCalls, RussianVoiceCommandParser.parse("Покажи пропущенные звонки", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadMessages, RussianVoiceCommandParser.parse("Прочитай сообщения", strictCommandsOnly = true))
        assertEquals(VoiceCommand.RingVolumeUp, RussianVoiceCommandParser.parse("Увеличь громкость звонка", strictCommandsOnly = true))
        assertEquals(
            VoiceCommand.PrepareMessage("дочери", "я уже дома"),
            RussianVoiceCommandParser.parse("Отправь сообщение дочери я уже дома", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.ReplyToMessage("маме", "скоро буду"),
            RussianVoiceCommandParser.parse("Ответь маме скоро буду", strictCommandsOnly = true)
        )
    }

    @Test
    fun `parses unified dictionary categories flashlight wifi bluetooth brightness volume system weather maps camera sos`() {
        // 1. Flashlight
        assertEquals(VoiceCommand.SetFlashlight(true), RussianVoiceCommandParser.parse("Мне нужен свет", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetFlashlight(false), RussianVoiceCommandParser.parse("Погаси свет", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetFlashlight(false), RussianVoiceCommandParser.parse("Выключи фонарик.", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ToggleFlashlight, RussianVoiceCommandParser.parse("Переключи фонарик", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CheckFlashlight, RussianVoiceCommandParser.parse("Фонарик включён?", strictCommandsOnly = true))

        // 2. Wi-Fi
        assertEquals(VoiceCommand.SetWifi(true), RussianVoiceCommandParser.parse("Включи вайфай", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetWifi(false), RussianVoiceCommandParser.parse("Отключи вай фай", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CheckWifiAndInternet, RussianVoiceCommandParser.parse("Есть интернет?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenWifiSettings, RussianVoiceCommandParser.parse("Открой настройки вайфая", strictCommandsOnly = true))

        // 3. Bluetooth
        assertEquals(VoiceCommand.SetBluetooth(true), RussianVoiceCommandParser.parse("Включи блютуз", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetBluetooth(false), RussianVoiceCommandParser.parse("Отключи блютуз", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CheckBluetooth, RussianVoiceCommandParser.parse("Подключены ли наушники?", strictCommandsOnly = true))

        // 4. Brightness
        assertEquals(VoiceCommand.SetBrightnessExtreme(true), RussianVoiceCommandParser.parse("Максимальная яркость", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetBrightnessExtreme(false), RussianVoiceCommandParser.parse("Минимальная яркость", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetAutoBrightness(true), RussianVoiceCommandParser.parse("Включи автояркость", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CheckBrightness, RussianVoiceCommandParser.parse("Какая яркость экрана", strictCommandsOnly = true))

        // 5. Volume & Speech
        assertEquals(VoiceCommand.VolumeMax, RussianVoiceCommandParser.parse("Звук на максимум", strictCommandsOnly = true))
        assertEquals(VoiceCommand.MediaVolumeUp, RussianVoiceCommandParser.parse("Громче видео", strictCommandsOnly = true))
        assertEquals(VoiceCommand.RingVolumeDown, RussianVoiceCommandParser.parse("Тише звонок", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CheckVolume, RussianVoiceCommandParser.parse("Какая громкость", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SpeakQuieter, RussianVoiceCommandParser.parse("Говори тише", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SpeakFaster, RussianVoiceCommandParser.parse("Говори быстрее", strictCommandsOnly = true))

        // 10 & 11. Screen reading & system
        assertEquals(VoiceCommand.ContinueReadingScreen, RussianVoiceCommandParser.parse("Читай дальше", strictCommandsOnly = true))
        assertEquals(VoiceCommand.StopSpeaking, RussianVoiceCommandParser.parse("Замолчи", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadCurrentApp, RussianVoiceCommandParser.parse("Где я нахожусь?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ClickUiElement("отправить"), RussianVoiceCommandParser.parse("Нажми отправить", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ClickUiElement("дальше"), RussianVoiceCommandParser.parse("Нажми дальше", strictCommandsOnly = true))
        assertEquals(VoiceCommand.LockScreen, RussianVoiceCommandParser.parse("Заблокируй экран", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenRecents, RussianVoiceCommandParser.parse("Последние приложения", strictCommandsOnly = true))
        assertEquals(VoiceCommand.TakeScreenshot, RussianVoiceCommandParser.parse("Сделай скриншот", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetAutoRotate(true), RussianVoiceCommandParser.parse("Включи автоповорот", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetDoNotDisturb(true), RussianVoiceCommandParser.parse("Включи не беспокоить", strictCommandsOnly = true))

        // 12, 13, 14, 15, 16, 17
        assertEquals(VoiceCommand.DisableAlarm, RussianVoiceCommandParser.parse("Выключи будильник", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SnoozeAlarm, RussianVoiceCommandParser.parse("Отложи будильник", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadReminders, RussianVoiceCommandParser.parse("Какие у меня напоминания", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CancelReminders, RussianVoiceCommandParser.parse("Отмени напоминания", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(), RussianVoiceCommandParser.parse("Погода", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(), RussianVoiceCommandParser.parse("Погода сегодня", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(), RussianVoiceCommandParser.parse("Какая погода?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(cityOverride = "москве"), RussianVoiceCommandParser.parse("Какая погода в Москве?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(tomorrow = true), RussianVoiceCommandParser.parse("Погода на завтра", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadWeather(askPrecipitation = true), RussianVoiceCommandParser.parse("Будет дождь?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadCurrencyRates, RussianVoiceCommandParser.parse("Сколько стоит доллар", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenMaps, RussianVoiceCommandParser.parse("Открой карты", strictCommandsOnly = true))
        assertEquals(VoiceCommand.NavigateToPlace("аптеки"), RussianVoiceCommandParser.parse("Построй маршрут до аптеки", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenCamera(false), RussianVoiceCommandParser.parse("Сделай фото", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenCamera(true), RussianVoiceCommandParser.parse("Запиши видео", strictCommandsOnly = true))
        assertEquals(VoiceCommand.CallEmergencyServices, RussianVoiceCommandParser.parse("Экстренный вызов", strictCommandsOnly = true))
        assertEquals(VoiceCommand.AssistantPingCheck, RussianVoiceCommandParser.parse("Ты меня слышишь?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.StopAssistantService, RussianVoiceCommandParser.parse("Закрой помощника", strictCommandsOnly = true))
        assertEquals(VoiceCommand.VisionNotSupportedHint("описание предметов"), RussianVoiceCommandParser.parse("Что передо мной?", strictCommandsOnly = true))
    }

    @Test
    fun `parses everyday reminders timers alarms`() {
        assertEquals(VoiceCommand.ReadTime, RussianVoiceCommandParser.parse("Который час?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadDate, RussianVoiceCommandParser.parse("Какое сегодня число?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.ReadDayOfWeek, RussianVoiceCommandParser.parse("Какой сегодня день недели?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetAlarm(null, null), RussianVoiceCommandParser.parse("Поставь будильник.", strictCommandsOnly = true))
        assertEquals(VoiceCommand.SetAlarm(7, 0), RussianVoiceCommandParser.parse("Поставь будильник на 7 утра", strictCommandsOnly = true))
        assertEquals(
            VoiceCommand.SetReminder("Принять лекарство", null, null, null),
            RussianVoiceCommandParser.parse("Напомни принять лекарство.", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.SetReminder("Выпить воды", null, null, 20),
            RussianVoiceCommandParser.parse("Напомни попить воды через 20 минут", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.SetTimer(10),
            RussianVoiceCommandParser.parse("Поставь таймер на 10 минут.", strictCommandsOnly = true)
        )
        assertEquals(
            VoiceCommand.SetTimer(5),
            RussianVoiceCommandParser.parse("Поставь таймер на пять минут", strictCommandsOnly = true)
        )
        assertEquals(VoiceCommand.ReadBattery, RussianVoiceCommandParser.parse("Сколько осталось зарядки?", strictCommandsOnly = true))
        assertEquals(VoiceCommand.FindMyPhone, RussianVoiceCommandParser.parse("Найди мой телефон", strictCommandsOnly = true))
    }

    @Test
    fun `parses universal open app commands`() {
        assertEquals(VoiceCommand.OpenApp("телеграм"), RussianVoiceCommandParser.parse("Открой телеграм", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenApp("сбербанк"), RussianVoiceCommandParser.parse("Зайди в Сбербанк", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenApp("озон"), RussianVoiceCommandParser.parse("Запусти озон", strictCommandsOnly = true))
        assertEquals(VoiceCommand.OpenApp("настройки"), RussianVoiceCommandParser.parse("Открой настройки", strictCommandsOnly = true))
    }

    @Test
    fun `matches installed apps by Russian aliases and transliteration without false positives`() {
        val sampleApps = listOf(
            InstalledAppEntry("org.telegram.messenger", "Telegram"),
            InstalledAppEntry("ru.sberbankmobile", "СберБанк"),
            InstalledAppEntry("ru.ozon.app.android", "OZON"),
            InstalledAppEntry("com.android.settings", "Настройки"),
            InstalledAppEntry("ru.yandex.yandexmaps", "Яндекс Карты"),
            InstalledAppEntry("ru.ok.android", "Одноклассники"),
            InstalledAppEntry("com.vkontakte.android", "ВКонтакте"),
            InstalledAppEntry("com.huawei.photos", "Галерея"),
            InstalledAppEntry("com.example.notes", "Заметки")
        )
        assertEquals("Telegram", AppLauncherController.findBestInstalledApp("телеграм", sampleApps)?.label)
        assertEquals("СберБанк", AppLauncherController.findBestInstalledApp("сбербанк", sampleApps)?.label)
        assertEquals("OZON", AppLauncherController.findBestInstalledApp("озон", sampleApps)?.label)
        assertEquals("Настройки", AppLauncherController.findBestInstalledApp("настройки", sampleApps)?.label)
        assertEquals("Яндекс Карты", AppLauncherController.findBestInstalledApp("яндекс карты", sampleApps)?.label)
        assertEquals("Одноклассники", AppLauncherController.findBestInstalledApp("ок", sampleApps)?.label)
        assertEquals("ВКонтакте", AppLauncherController.findBestInstalledApp("вк", sampleApps)?.label)
        assertEquals("Галерея", AppLauncherController.findBestInstalledApp("галерею", sampleApps)?.label)
        assertEquals("Заметки", AppLauncherController.findBestInstalledApp("блокнот", sampleApps)?.label)
        // Must NEVER open Одноклассники for "тикток" or ВКонтакте for "вкусвилл"
        assertNull(AppLauncherController.findBestInstalledApp("тикток", sampleApps))
        assertNull(AppLauncherController.findBestInstalledApp("вкусвилл", sampleApps))
        assertEquals("telegram", AppLauncherController.transliterateRuToEn("телеграм"))
    }

    @Test
    fun `parses disambiguation index and confirmation only when pending`() {
        assertTrue(
            RussianVoiceCommandParser.parse(
                "да",
                strictCommandsOnly = true,
                hasPendingConfirmation = false
            ) is VoiceCommand.Unknown
        )
        assertTrue(
            RussianVoiceCommandParser.parse(
                "первый",
                strictCommandsOnly = true,
                hasPendingConfirmation = false
            ) is VoiceCommand.Unknown
        )
        assertEquals(
            VoiceCommand.ConfirmAction,
            RussianVoiceCommandParser.parse(
                "да",
                strictCommandsOnly = true,
                hasPendingConfirmation = true
            )
        )
        assertEquals(
            VoiceCommand.SelectDisambiguationIndex(0),
            RussianVoiceCommandParser.parse(
                "первый",
                strictCommandsOnly = true,
                hasPendingConfirmation = true
            )
        )
        assertEquals(
            VoiceCommand.SelectDisambiguationIndex(1),
            RussianVoiceCommandParser.parse(
                "второй",
                strictCommandsOnly = true,
                hasPendingConfirmation = true
            )
        )
    }

    @Test
    fun `matches Russian inflected WhatsApp contacts accurately`() {
        val contacts = listOf(
            ContactMatchCandidate("Мама", voipDataId = 101L, profileDataId = null, phoneNumber = "+79001112233"),
            ContactMatchCandidate("Иван Петров", voipDataId = 102L, profileDataId = null, phoneNumber = "+79004445566"),
            ContactMatchCandidate("Сестра Анна", voipDataId = null, profileDataId = 103L, phoneNumber = "+79007778899"),
            ContactMatchCandidate("Дочь Елена", voipDataId = 104L, profileDataId = null, phoneNumber = "+79008889900")
        )
        assertEquals("Мама", WhatsAppContactCaller.findBestContactMatch("маме", contacts)?.displayName)
        assertEquals("Иван Петров", WhatsAppContactCaller.findBestContactMatch("ивану", contacts)?.displayName)
        assertEquals("Сестра Анна", WhatsAppContactCaller.findBestContactMatch("анне", contacts)?.displayName)
        assertEquals("Дочь Елена", WhatsAppContactCaller.findBestContactMatch("дочери", contacts)?.displayName)
        assertNull(WhatsAppContactCaller.findBestContactMatch("неизвестный", contacts))
    }

    @Test
    fun `strict mode ignores ambient speech and does not trigger false commands or tts loops`() {
        val noisySamples = listOf(
            "сегодня на улице хорошая погода",
            "включи телевизор",
            "мне нужна вода",
            "я тебе напомню потом",
            "у нас скоро будильник",
            "посмотри какой таймер",
            "ну все хватит",
            "если что позвони мне потом"
        )
        for (sample in noisySamples) {
            val res = RussianVoiceCommandParser.parse(sample, strictCommandsOnly = true)
            assertTrue("Expected Unknown without repeat prompt for '$sample', got $res", res is VoiceCommand.Unknown && !res.shouldPromptRepeat)
        }

        val addressed = RussianVoiceCommandParser.parse(
            "Слушай абракадабра",
            requireWakeWord = false,
            wakeWord = "слушай",
            strictCommandsOnly = true
        )
        assertTrue(addressed is VoiceCommand.Unknown && addressed.shouldPromptRepeat)
    }

    @Test
    fun `filters noisy partial transcripts from ui preview`() {
        assertFalse(RussianVoiceCommandParser.isLikelyCommandPrefix("привет как дела"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("открой ютуб"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("открой вацап"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("открой телеграм"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("позвони дочери"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("включи фонарик"))
        assertTrue(RussianVoiceCommandParser.isLikelyCommandPrefix("что я пропустил"))
    }

    @Test
    fun `respects optional wake word setting`() {
        assertNull(
            RussianVoiceCommandParser.parse(
                rawText = "закрой",
                requireWakeWord = true,
                wakeWord = "слушай",
                strictCommandsOnly = true
            )
        )
        assertEquals(
            VoiceCommand.CloseYouTube,
            RussianVoiceCommandParser.parse(
                rawText = "Слушай, закрой",
                requireWakeWord = true,
                wakeWord = "слушай",
                strictCommandsOnly = true
            )
        )
    }
}
