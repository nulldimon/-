package com.example.voice

object RussianVoiceCommandParser {

    // Includes common Russian offline ASR phonetic variants for "YouTube"
    private val youtubeAliases = listOf(
        "ютуб", "ютубе", "ютуба", "ютубу", "ютьюб", "ютьюбе",
        "ютюб", "ютюбе", "ю туб", "ю тубе", "ю туба",
        "ю дуб", "ю дубе", "юдуб", "утуб", "итуб", "youtube"
    )

    private val whatsappAliases = listOf(
        "вацап", "вацапе", "вацапу", "вацапа", "вацапп",
        "ватсап", "ватсапе", "ватсапу", "ватсапа", "ватсапп",
        "вотсап", "вотсапе", "вотсапп", "ватцап", "васап",
        "вацаб", "whatsapp"
    )

    private val closeExactWords = setOf(
        "закрой", "закрыть", "закрывай", "стоп", "выход",
        "выйди", "выйти", "хватит", "убери"
    )

    private val closeMultiPhrases = listOf(
        "закрой ютуб", "закрыть ютуб", "закрой ютьюб", "закрой видео", "выйди из видео",
        "выключи ютуб", "выключить ютуб", "отключи ютуб", "выйди из ютуба",
        "выйти из ютуба", "убери ютуб", "закрой браузер", "закрой плеер",
        "останови ютуб", "закрыть видео", "выход из ютуба"
    )

    private val urgentSingleWords = setOf(
        "закрой", "закрыть", "стоп", "выход", "выйди", "пауза", "тише", "громче", "отмена", "замолчи"
    )

    private val numberWords = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "час" to 1,
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18,
        "девятнадцать" to 19, "двадцать" to 20, "двадцать один" to 21, "двадцать два" to 22,
        "двадцать три" to 23, "тридцать" to 30, "сорок" to 40, "сорок пять" to 45,
        "пятьдесят" to 50, "шестьдесят" to 60
    )

    private val politePrefixes = setOf(
        "пожалуйста", "ну", "давай", "быстро", "сделай", "а", "так", "вот", "скажи", "подскажи"
    )

    fun isUrgentPartialCommand(partialText: String): Boolean {
        val norm = normalize(partialText)
        return norm in urgentSingleWords
    }

    /**
     * Used to keep the UI silent when surrounding noise/TV chatter is picked up by Vosk.
     */
    fun isLikelyCommandPrefix(partialText: String): Boolean {
        val norm = normalize(partialText)
        if (norm.isBlank()) return false
        return VoiceCommandCatalog.allPreviewKeywords.any { kw -> norm.contains(kw) }
    }

    fun parse(
        rawText: String,
        requireWakeWord: Boolean = false,
        wakeWord: String = "слушай",
        strictCommandsOnly: Boolean = false,
        hasPendingConfirmation: Boolean = false
    ): VoiceCommand? {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return null

        val wake = normalize(wakeWord).ifBlank { "слушай" }
        var usedWakeWord = false
        val commandText = if (requireWakeWord) {
            when {
                normalized == wake || normalized == "помощник" || normalized == "привет помощник" ->
                    return VoiceCommand.AssistantWakeCheck
                normalized.startsWith("$wake ") -> {
                    usedWakeWord = true
                    normalized.removePrefix("$wake ").trim()
                }
                normalized.contains(" $wake ") -> {
                    usedWakeWord = true
                    normalized.substringAfter(" $wake ").trim()
                }
                else -> return null
            }
        } else {
            if (normalized == wake || normalized == "помощник" || normalized == "привет помощник") {
                return VoiceCommand.AssistantWakeCheck
            }
            if (normalized.startsWith("$wake ")) {
                usedWakeWord = true
                normalized.removePrefix("$wake ").trim()
            } else {
                normalized
            }
        }

        if (commandText.isBlank()) return null
        val wordCount = commandText.split(" ").size

        // 0. Pending verbal confirmation & disambiguation selection ("Да", "Первый", "Второй")
        if (hasPendingConfirmation) {
            if (matchesShortOrSuffix(
                    commandText,
                    wordCount,
                    setOf("да", "подтверждаю", "подтвердить", "отправляй", "отправь", "верно", "конечно", "да звони", "звони", "позвони")
                )
            ) {
                return VoiceCommand.ConfirmAction
            }
            when (commandText) {
                "первый", "первую", "первого", "первый контакт", "1", "один" ->
                    return VoiceCommand.SelectDisambiguationIndex(0)
                "второй", "вторую", "второго", "второй контакт", "2", "два" ->
                    return VoiceCommand.SelectDisambiguationIndex(1)
                "третий", "третью", "третьего", "третий контакт", "3", "три" ->
                    return VoiceCommand.SelectDisambiguationIndex(2)
            }
        }

        // Cancel command works anytime (stops confirmation, FindMyPhone ring, or current speech)
        if (matchesShortOrSuffix(
                commandText,
                wordCount,
                setOf("отмена", "отмени", "отменить", "не надо", "забудь", "остановись", "прекрати")
            )
        ) {
            return VoiceCommand.CancelAction
        }

        // 1. Check specific WhatsApp close BEFORE generic CloseYouTube
        if (commandText in setOf("закрой ватсап", "закрой вацап", "выйди из ватсапа", "выйди из вацапа")) {
            return VoiceCommand.CloseWhatsApp
        }

        // 2. YouTube search ("найди видео про ...", "открой ютуб и найди ...") BEFORE generic catalog/app open
        extractExplicitVideoSearchQuery(commandText)?.let { query ->
            return VoiceCommand.OpenYouTubeSearch(query)
        }
        extractYouTubeSearchQuery(commandText)?.let { query ->
            return if (query.isNotBlank()) {
                VoiceCommand.OpenYouTubeSearch(query)
            } else {
                VoiceCommand.OpenYouTubeHome
            }
        }
        if (isOpenYouTubeHome(commandText)) {
            return VoiceCommand.OpenYouTubeHome
        }

        // 3. Preset YouTube searches ("Найди новости", "Найди музыку", "Найди фильм", "Найди мультики", "Найди концерт")
        val presetSearches = mapOf(
            setOf("найди новости", "покажи новости", "расскажи новости", "включи новости", "что происходит в мире") to "новости",
            setOf("найди музыку", "покажи музыку", "включи музыку") to "музыка",
            setOf("найди фильм", "найди фильмы", "покажи фильм", "покажи фильмы", "включи фильм") to "фильмы",
            setOf("найди мультики", "найди мультфильмы", "покажи мультики", "включи мультики") to "мультики",
            setOf("найди концерт", "найди концерты", "покажи концерт", "включи концерт") to "концерты"
        )
        for ((phrases, query) in presetSearches) {
            if (matchesShortOrSuffix(commandText, wordCount, phrases)) {
                return VoiceCommand.OpenYouTubeSearch(query)
            }
        }

        // 4. Unified Dictionary Lookup (Section 18 catalog covering all 17 categories)
        findCatalogMatch(commandText, wordCount)?.let { return it }

        // 4b. Parameterized Weather with city ("погода в Москве", "какая погода завтра в Казани")
        extractWeatherWithCity(commandText)?.let { return it }

        // 5. Close YouTube / current app (one word, polite prefix + word, or explicit close phrase)
        val firstToken = commandText.substringBefore(' ')
        val secondToken = commandText.substringAfter(' ', "")
        if (commandText in closeExactWords ||
            closeMultiPhrases.any { commandText == it || (firstToken in politePrefixes && commandText.removePrefix("$firstToken ").trim() == it) } ||
            (wordCount == 2 && firstToken in politePrefixes && secondToken in closeExactWords)
        ) {
            return VoiceCommand.CloseYouTube
        }

        // 6. Camera / Vision descriptive commands (honest guidance without faking)
        val visionPhrases = mapOf(
            setOf("что передо мной", "опиши предмет", "что находится впереди", "что у меня в руках") to "описание предметов",
            setOf("прочитай текст камерой", "прочитай надпись", "что здесь написано", "прочитай упаковку", "что написано на коробке", "прочитай документ", "прочитай бумагу", "найди надпись", "прочитай текст перед камерой") to "распознавание текста камерой",
            setOf("что это за купюра", "определи номинал") to "распознавание купюр",
            setOf("какого цвета предмет", "назови цвет", "какого цвета эта вещь") to "определение цвета"
        )
        for ((phrases, feature) in visionPhrases) {
            if (matchesShortOrSuffix(commandText, wordCount, phrases)) {
                return VoiceCommand.VisionNotSupportedHint(feature)
            }
        }

        // 7. Maps & Navigation with place parameter ("построй маршрут до аптеки", "где ближайшая больница")
        extractNavigationQuery(commandText)?.let { place ->
            return VoiceCommand.NavigateToPlace(place)
        }

        // 8. Parameterized Alarms, Timers & Reminders (strictly command-initiated, not mid-sentence)
        if (commandText.startsWith("напомни") ||
            commandText.startsWith("поставь напоминание") ||
            commandText.startsWith("создай напоминание")
        ) {
            val (h, m) = extractTimeHourMinute(commandText)
            val inMins = extractMinutesDuration(commandText)
            val title = extractReminderTitle(commandText)
            return VoiceCommand.SetReminder(title = title, hour = h, minute = m, inMinutes = inMins)
        }

        if (commandText.startsWith("поставь будильник") ||
            commandText.startsWith("установи будильник") ||
            commandText.startsWith("заведи будильник") ||
            commandText.startsWith("разбуди ") ||
            commandText == "будильник"
        ) {
            val (h, m) = extractTimeHourMinute(commandText)
            return VoiceCommand.SetAlarm(hour = h, minute = m)
        }

        if (commandText.startsWith("поставь таймер") ||
            commandText.startsWith("установи таймер") ||
            commandText.startsWith("включи таймер") ||
            commandText.startsWith("таймер ") ||
            commandText == "таймер" ||
            commandText.startsWith("засечь ") ||
            commandText.startsWith("засеки ")
        ) {
            val mins = extractMinutesDuration(commandText) ?: 10
            return VoiceCommand.SetTimer(minutes = mins)
        }

        // 9. Message Preparation & Reply ("Напиши маме ...", "Отправь сообщение маме ...", "Ответь маме ...")
        extractReplyCommand(commandText)?.let { (recipient, body) ->
            return VoiceCommand.ReplyToMessage(recipient, body)
        }
        extractMessagePreparation(commandText)?.let { (recipient, body) ->
            return VoiceCommand.PrepareMessage(recipient, body)
        }

        // 10. Video call & Call by phone number & Call contact
        extractVideoCallContact(commandText)?.let { contact ->
            return VoiceCommand.VideoCallContact(contact)
        }
        extractDialNumberDigits(commandText)?.let { digits ->
            return VoiceCommand.CallPhoneNumber(digits)
        }
        extractCallContact(commandText)?.let { (contactName, preferWa) ->
            return VoiceCommand.CallContact(contactName, preferWhatsApp = preferWa)
        }

        // 11. Screen element interaction ("нажми <кнопка>", "кликни <текст>")
        extractUiClickTarget(commandText)?.let { target ->
            return VoiceCommand.ClickUiElement(target)
        }

        // 12. Universal App Launcher ("открой <любое приложение>", "запусти <приложение>")
        extractUniversalAppName(commandText)?.let { appName ->
            return VoiceCommand.OpenApp(appName)
        }

        // 13. Non-strict UI extras
        if (!strictCommandsOnly) {
            val genericSearchPrefixes = listOf("найди ", "найти ", "поищи ")
            for (prefix in genericSearchPrefixes) {
                if (commandText.startsWith(prefix)) {
                    val q = cleanQuery(commandText.removePrefix(prefix))
                    if (q.isNotBlank()) return VoiceCommand.OpenYouTubeSearch(q)
                }
            }
            when (commandText) {
                "ниже", "вниз", "листай вниз", "прокрути вниз", "крути вниз" -> return VoiceCommand.ScrollDown
                "выше", "вверх", "листай вверх", "прокрути вверх", "крути вверх" -> return VoiceCommand.ScrollUp
                "первое", "первое видео", "открой первое", "включи первое", "первый ролик", "запусти первое" ->
                    return VoiceCommand.OpenFirstResult
                "заряд", "батарея", "заряд батареи" -> return VoiceCommand.ReadBattery
            }
        }

        // Only prompt repeat when explicitly addressed with wake word or in non-strict mode
        val startedExplicitCommand = usedWakeWord || !strictCommandsOnly
        return VoiceCommand.Unknown(commandText, shouldPromptRepeat = startedExplicitCommand)
    }

    private fun findCatalogMatch(text: String, wordCount: Int): VoiceCommand? {
        // Pass 1: O(1) exact full phrase match across entire catalog
        VoiceCommandCatalog.exactPhraseMap[text]?.let { return it.buildCommand() }

        // Pass 2: polite/filler prefix stripped exact match (e.g. "пожалуйста выключи фонарик", "скажи какая погода")
        if (wordCount in 2..6) {
            val firstWord = text.substringBefore(' ')
            if (firstWord in politePrefixes) {
                val remainder = text.substringAfter(' ').trim()
                VoiceCommandCatalog.exactPhraseMap[remainder]?.let { return it.buildCommand() }
            }
        }
        return null
    }

    private fun extractWeatherWithCity(text: String): VoiceCommand.ReadWeather? {
        val tomorrow = text.contains("завтра")
        val askRain = text.contains("дожд") || text.contains("снег") || text.contains("осадк")
        val cityMatch = Regex(
            """^(?:какая\s+погода|погода|прогноз\s+погоды|будет\s+ли\s+дождь|пойдет\s+ли\s+дождь)(?:\s+завтра|\s+сегодня|\s+сейчас)?\s+(?:в|во)\s+([а-яa-z\- ]{3,30})$"""
        ).find(text) ?: return null
        val city = cleanQuery(cityMatch.groupValues[1])
        return if (city.isNotBlank()) {
            VoiceCommand.ReadWeather(tomorrow = tomorrow, askPrecipitation = askRain, cityOverride = city)
        } else null
    }

    private fun extractExplicitVideoSearchQuery(text: String): String? {
        val videoSearchRegex = Regex("""^(?:найди|найти|покажи|включи)\s+(?:видео|ролик|ролики)\s+(?:про\s+|о\s+|об\s+)?(.+)$""")
        val m = videoSearchRegex.find(text) ?: return null
        val q = cleanQuery(m.groupValues[1])
        return q.ifBlank { null }
    }

    private fun extractNavigationQuery(text: String): String? {
        val navPatterns = listOf(
            Regex("""^(?:построй|проложи)\s+маршрут\s+(?:до\s+|в\s+|на\s+)?(.+)$"""),
            Regex("""^как\s+добраться\s+(?:до\s+)?(.+)$"""),
            Regex("""^веди\s+меня\s+(?:в\s+|до\s+)?(.+)$"""),
            Regex("""^где\s+ближайш(?:ая|ий|ее|ие)\s+(.+)$"""),
            Regex("""^(?:найди|найти|покажи)\s+ближайш(?:ую|ий|ее)\s+(.+)$""")
        )
        for (regex in navPatterns) {
            val match = regex.find(text)
            if (match != null) {
                val target = match.groupValues[1].trim()
                if (target.isNotBlank()) return cleanQuery(target)
            }
        }
        return null
    }

    private fun extractReminderTitle(text: String): String {
        if (text.contains("лекарств") || text.contains("таблетк")) return "Принять лекарство"
        if (text.contains("вод")) return "Выпить воды"
        var clean = text
        val prefixes = listOf(
            "поставь напоминание ", "создай напоминание ", "напомни мне ", "напомни "
        )
        for (p in prefixes) {
            if (clean.startsWith(p)) {
                clean = clean.removePrefix(p).trim()
                break
            }
        }
        clean = clean
            .replace(Regex("""\s+(?:в|на)\s+\d{1,2}(?:\s*[: ]\s*\d{2})?(?:\s*(?:час|часа|часов|утра|вечера))?$"""), "")
            .replace(Regex("""\s+через\s+\d+\s*(?:минут|минуты|минуту|мин)?$"""), "")
            .trim()
        return clean.ifBlank { "Напоминание" }.replaceFirstChar { it.uppercase() }
    }

    private fun extractReplyCommand(text: String): Pair<String?, String?>? {
        if (text in setOf("ответь на сообщение", "продиктуй ответ")) {
            return null to null
        }
        val replyMsg = Regex("""^ответь\s+([^\s]+)\s+(.+)$""").find(text)
        if (replyMsg != null) {
            val target = replyMsg.groupValues[1]
            if (target !in setOf("на", "звонок")) {
                return target to cleanQuery(replyMsg.groupValues[2])
            }
        }
        val replyOnly = Regex("""^ответь\s+([^\s]+)$""").find(text)
        if (replyOnly != null) {
            val target = replyOnly.groupValues[1]
            if (target !in setOf("на", "звонок")) {
                return target to null
            }
        }
        return null
    }

    private fun extractMessagePreparation(text: String): Pair<String, String>? {
        val msgRegex = Regex("""(?:^|\s)(?:отправь|отправить|пошли|напиши)\s+сообщение\s+(\S+)\s+(.+)$""")
        val textRegex = Regex("""(?:^|\s)отправь\s+(\S+)\s+текст\s+(.+)$""")
        val simpleWriteRegex = Regex("""(?:^|\s)напиши\s+(?:в\s+вацап\s+|в\s+ватсап\s+|по\s+вацапу\s+)?(\S+)\s+(.+)$""")
        val match = msgRegex.find(text) ?: textRegex.find(text) ?: simpleWriteRegex.find(text) ?: return null
        val recipient = match.groupValues[1].trim()
        var body = match.groupValues[2].trim()
        for (prefix in listOf("что ", "текст ", "сообщение ")) {
            if (body.startsWith(prefix)) {
                body = body.removePrefix(prefix).trim()
            }
        }
        if (recipient.isBlank() || body.isBlank()) return null
        return recipient to body
    }

    private fun extractVideoCallContact(text: String): String? {
        val m1 = Regex("""^(?:видеозвонок|видеовызов|видео звонок)\s+(.+)$""").find(text)
        if (m1 != null) return cleanQuery(m1.groupValues[1]).ifBlank { null }
        val m2 = Regex("""^(?:позвони|набери)\s+(.+?)\s+(?:с\s+видео|по\s+видео|видеосвязью)$""").find(text)
        if (m2 != null) return cleanQuery(m2.groupValues[1]).ifBlank { null }
        return null
    }

    private fun extractDialNumberDigits(text: String): String? {
        val m = Regex("""^(?:позвони|набери|вызови)\s+(?:на\s+)?номер\s+(.+)$""").find(text) ?: return null
        val rawTail = m.groupValues[1].trim()
        val spokenDigitsMap = mapOf(
            "ноль" to "0", "один" to "1", "два" to "2", "три" to "3", "четыре" to "4",
            "пять" to "5", "шесть" to "6", "семь" to "7", "восемь" to "8", "девять" to "9", "плюс" to "+"
        )
        val built = buildString {
            for (token in rawTail.split(" ")) {
                val mapped = spokenDigitsMap[token]
                if (mapped != null) {
                    append(mapped)
                } else {
                    append(token.filter { it.isDigit() || it == '+' })
                }
            }
        }
        return built.takeIf { it.count { c -> c.isDigit() } >= 3 }
    }

    private fun extractCallContact(text: String): Pair<String, Boolean>? {
        val patterns = listOf(
            Regex("""^(?:сделай\s+звонок|позвони|позвонить|набери|набрать|вызови|звони)\s+(.+)$"""),
            Regex("""^хочу\s+поговорить\s+с\s+(.+)$"""),
            Regex("""^свяжись\s+с\s+(.+)$""")
        )
        var rest: String? = null
        for (p in patterns) {
            val match = p.find(text)
            if (match != null) {
                rest = match.groupValues[1].trim()
                break
            }
        }
        if (rest == null) return null

        var preferWhatsApp = false
        val waPrepositions = listOf("по ", "в ", "на ", "через ")
        var changed = true
        while (changed) {
            changed = false
            for (filler in listOf("пожалуйста ", "мне ", "срочно ")) {
                if (rest!!.startsWith(filler)) {
                    rest = rest!!.removePrefix(filler).trim()
                    changed = true
                }
            }
            for (alias in whatsappAliases.sortedByDescending { it.length }) {
                for (prep in waPrepositions) {
                    val prefix = "$prep$alias "
                    val exactTail = " $prep$alias"
                    if (rest!!.startsWith(prefix)) {
                        rest = rest!!.removePrefix(prefix).trim()
                        preferWhatsApp = true
                        changed = true
                    } else if (rest!!.endsWith(exactTail)) {
                        rest = rest!!.removeSuffix(exactTail).trim()
                        preferWhatsApp = true
                        changed = true
                    }
                }
                if (rest!!.startsWith("$alias ")) {
                    rest = rest!!.removePrefix("$alias ").trim()
                    preferWhatsApp = true
                    changed = true
                } else if (rest!!.endsWith(" $alias")) {
                    rest = rest!!.removeSuffix(" $alias").trim()
                    preferWhatsApp = true
                    changed = true
                }
            }
        }

        val cleaned = cleanQuery(rest.orEmpty())
        return if (cleaned.isNotBlank()) cleaned to preferWhatsApp else null
    }

    private fun extractUiClickTarget(text: String): String? {
        val m = Regex("""^(?:нажми|кликни|тапни)\s+(?:на\s+|кнопку\s+)?(.+)$""").find(text) ?: return null
        return cleanQuery(m.groupValues[1]).ifBlank { null }
    }

    private fun extractUniversalAppName(text: String): String? {
        val prefixes = listOf(
            "открой приложение ", "открыть приложение ",
            "запусти приложение ", "запустить приложение ",
            "открой ", "открыть ", "запусти ", "запустить ",
            "зайди в ", "перейди в "
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                val rest = cleanQuery(text.removePrefix(prefix))
                if (rest.isNotBlank() && rest.split(" ").size <= 4) {
                    return rest
                }
            }
        }
        return null
    }

    private fun extractMinutesDuration(text: String): Int? {
        val digitMatch = Regex("""(\d+)\s*(?:минут|минуты|минуту|мин)?""").find(text)
        if (digitMatch != null) {
            val v = digitMatch.groupValues[1].toIntOrNull()
            if (v != null && v in 1..720) return v
        }
        for ((word, value) in numberWords.entries.sortedByDescending { it.key.length }) {
            if (value > 0 && Regex("""(?:^|\s)$word(?:\s|$)""").containsMatchIn(text)) {
                return value
            }
        }
        return null
    }

    private fun extractTimeHourMinute(text: String): Pair<Int?, Int?> {
        val hhmmMatch = Regex("""(\d{1,2})\s*[: ]\s*(\d{2})""").find(text)
        if (hhmmMatch != null) {
            val h = hhmmMatch.groupValues[1].toIntOrNull()
            val m = hhmmMatch.groupValues[2].toIntOrNull()
            if (h != null && h in 0..23 && m != null && m in 0..59) {
                return h to m
            }
        }
        val hourMatch = Regex("""(?:на|в)\s+(\d{1,2})(?:\s*(?:час|часа|часов|утра|вечера))?""").find(text)
        if (hourMatch != null) {
            var h = hourMatch.groupValues[1].toIntOrNull()
            if (h != null && h in 0..23) {
                if (text.contains("вечера") && h in 1..11) h += 12
                return h to 0
            }
        }
        for ((word, value) in numberWords.entries.sortedByDescending { it.key.length }) {
            if (value in 0..23 && Regex("""(?:^|\s)(?:на|в)\s+$word(?:\s|$)""").containsMatchIn(text)) {
                var h = value
                if (text.contains("вечера") && h in 1..11) h += 12
                return h to 0
            }
        }
        return null to null
    }

    private fun matchesShortOrSuffix(text: String, wordCount: Int, phrases: Set<String>): Boolean {
        if (text in phrases) return true
        if (wordCount <= 5) {
            return phrases.any { phrase ->
                if (phrase.contains(' ')) {
                    text.endsWith(" $phrase")
                } else {
                    wordCount == 2 && text.endsWith(" $phrase")
                }
            }
        }
        return false
    }

    private fun isOpenYouTubeHome(text: String): Boolean {
        val openVerbs = listOf("открой", "открыть", "запусти", "запустить", "включи", "включить", "покажи", "зайди на", "зайди в", "перейди в")
        if (text in youtubeAliases) return true
        return openVerbs.any { verb ->
            youtubeAliases.any { yt ->
                text == "$verb $yt" ||
                    text == "$yt $verb" ||
                    text.endsWith(" $verb $yt")
            }
        }
    }

    private fun extractYouTubeSearchQuery(text: String): String? {
        val matchedAlias = youtubeAliases
            .sortedByDescending { it.length }
            .firstOrNull { alias ->
                text == alias ||
                    text.startsWith("$alias ") ||
                    text.endsWith(" $alias") ||
                    text.contains(" $alias ")
            } ?: return null

        if (isOpenYouTubeHome(text)) return null

        val aliasIdx = text.indexOf(matchedAlias)
        val afterAlias = text.substring(aliasIdx + matchedAlias.length).trim()
        val beforeAlias = text.substring(0, aliasIdx).trim()

        val candidateAfter = stripLeadingConnectors(afterAlias)
        if (candidateAfter.isNotBlank()) {
            return cleanQuery(candidateAfter)
        }

        val trimmedBefore = beforeAlias
            .removeSuffix(" на")
            .removeSuffix(" в")
            .removeSuffix(" по")
            .trim()

        val searchAnchorRegex = Regex("""(?:^|\s)(найди|найти|поищи|поиск|включи|включить|покажи|показать)\s+(.+)$""")
        val anchorMatch = searchAnchorRegex.find(trimmedBefore)
        if (anchorMatch != null) {
            val q = cleanQuery(stripLeadingConnectors(anchorMatch.groupValues[2]))
            if (q.isNotBlank()) return q
        }

        return null
    }

    private fun stripLeadingConnectors(input: String): String {
        var result = input.trim()
        val leadWords = listOf(
            "и ", "а ", "пожалуйста ", "мне ",
            "открой ", "открыть ", "запусти ", "запустить ",
            "найди ", "найти ", "поищи ", "поиск ",
            "включи ", "включить ", "покажи ", "показать ",
            "поставь ", "поставить ",
            "на ", "в ", "по ", "там "
        )
        var changed = true
        while (changed) {
            changed = false
            for (word in leadWords) {
                if (result.startsWith(word)) {
                    result = result.removePrefix(word).trim()
                    changed = true
                }
            }
        }
        return result
    }

    private fun cleanQuery(input: String): String {
        return input
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun normalize(raw: String): String {
        return raw
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[.,!?;:"'«»()—–\-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
