package com.example.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.voice.RussianVoiceCommandParser
import kotlin.math.max
import kotlin.math.min

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val activityName: String = "",
    val altLabel: String = ""
)

sealed class AppLaunchResult {
    data class Launched(val appLabel: String) : AppLaunchResult()
    data class NotFound(val query: String) : AppLaunchResult()
    data class Failed(val reason: String) : AppLaunchResult()
}

object AppLauncherController {

    private val russianCaseOverrides = mapOf(
        "настройку" to "настройки",
        "настройках" to "настройки",
        "камеру" to "камера",
        "галерею" to "галерея",
        "почту" to "почта",
        "музыку" to "музыка",
        "телегу" to "телеграм",
        "телеграмм" to "телеграм",
        "телеграмма" to "телеграм",
        "книгу" to "книги",
        "погоду" to "погода",
        "карту" to "карты",
        "заметку" to "заметки",
        "звонилку" to "телефон",
        "контактах" to "контакты",
        "вайберу" to "вайбер",
        "вибер" to "вайбер",
        "сбербанке" to "сбербанк",
        "сберу" to "сбер",
        "тинькофф" to "тинькофф",
        "тинькоф" to "тинькофф",
        "рутубе" to "рутуб",
        "ватсапе" to "whatsapp",
        "вацапе" to "whatsapp"
    )

    private val russianAliasKeywords: List<Pair<List<String>, List<String>>> = listOf(
        listOf("браузер", "интернет", "хром", "chrome", "яндекс браузер") to listOf("browser", "chrome", "yandex", "opera", "firefox", "браузер", "интернет"),
        listOf("камера", "фотоаппарат") to listOf("camera", "камера"),
        listOf("галерея", "фотографии", "альбом", "снимки") to listOf("gallery", "photos", "галерея", "альбом"),
        listOf("карты", "навигатор", "навигация", "маршрут", "яндекс карты", "2гис", "два гис") to listOf("maps", "yandexmaps", "navigator", "2gis", "petalmaps", "карты", "навигатор", "2гис"),
        listOf("яндекс музыка") to listOf("yandex.music", "яндекс музыка", "music"),
        listOf("яндекс такси", "такси", "яндекс го", "яндекс go") to listOf("taxi", "yandex.taxi", "такси", "uber", "maxim"),
        listOf("телефон", "звонилка", "набор номера") to listOf("dialer", "telecom", "телефон"),
        listOf("контакты", "телефонная книга", "адресная книга") to listOf("contacts", "контакты"),
        listOf("настройки", "параметры") to listOf("settings", "настройки"),
        listOf("сообщения", "смс", "sms") to listOf("mms", "messaging", "messages", "сообщения"),
        listOf("телеграм", "телега", "telegram") to listOf("telegram", "телеграм"),
        listOf("ватсап", "вацап", "вотсап", "whatsapp") to listOf("whatsapp", "ватсап"),
        listOf("вайбер", "viber") to listOf("viber", "вайбер"),
        listOf("вк", "вконтакте", "в контакте") to listOf("vkontakte", "vkvideo", "вконтакте"),
        listOf("одноклассники", "ок") to listOf("odnoklassniki", "одноклассники"),
        listOf("сбер", "сбербанк", "сбербанк онлайн", "сбол") to listOf("sberbank", "sbol", "сбербанк", "сбер", "сбол"),
        listOf("тинькофф", "т банк", "тинькоф") to listOf("tinkoff", "tbank", "тинькофф", "тбанк"),
        listOf("втб") to listOf("vtb", "втб"),
        listOf("альфа", "альфа банк") to listOf("alfabank", "альфа"),
        listOf("госуслуги") to listOf("gosuslugi", "госуслуги"),
        listOf("авито") to listOf("avito", "авито"),
        listOf("почта", "email", "мейл", "письма") to listOf("mail", "gmail", "yandex.mail", "почта"),
        listOf("часы", "будильник", "таймер") to listOf("deskclock", "clock", "часы", "будильник"),
        listOf("калькулятор") to listOf("calculator", "калькулятор"),
        listOf("календарь") to listOf("calendar", "календарь"),
        listOf("заметки", "блокнот", "записи") to listOf("notepad", "notes", "keep", "заметки", "блокнот"),
        listOf("музыка", "плеер") to listOf("music", "player", "музыка"),
        listOf("радио") to listOf("radio", "радио"),
        listOf("диктофон", "запись голоса") to listOf("soundrecorder", "recorder", "диктофон"),
        listOf("файлы", "проводник", "менеджер файлов") to listOf("filemanager", "files", "проводник", "файлы"),
        listOf("погода") to listOf("weather", "погода"),
        listOf("здоровье", "шаги") to listOf("health", "здоровье"),
        listOf("книги", "читалка", "литрес") to listOf("litres", "fbreader", "books", "литрес", "книги"),
        listOf("рутуб", "rutube") to listOf("rutube", "рутуб"),
        listOf("дзен", "яндекс дзен") to listOf("dzen", "zen", "дзен"),
        listOf("кинопоиск") to listOf("kinopoisk", "кинопоиск"),
        listOf("тикток", "тик ток", "tiktok") to listOf("tiktok", "musically", "тикток"),
        listOf("яндекс") to listOf("yandex", "searchapp", "яндекс"),
        listOf("озон", "ozon") to listOf("ozon", "озон"),
        listOf("вайлдберриз", "вайлдберрис", "валдберис", "wildberries") to listOf("wildberries", "вайлдберриз"),
        listOf("рустор", "rustore", "аппгалери", "аппгэллери", "appgallery", "магазин приложений") to listOf("rustore", "appmarket", "appgallery", "rustore")
    )

    fun openAppBySpokenName(context: Context, rawQuery: String): AppLaunchResult {
        val norm = cleanAppQuery(RussianVoiceCommandParser.normalize(rawQuery))
        if (norm.isBlank()) return AppLaunchResult.NotFound(rawQuery)

        // Direct reliable system intents ONLY for unambiguous core system apps
        when (norm) {
            "настройки", "параметры" -> {
                val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (YouTubeLauncher.startActivityReliably(context, intent)) {
                    return AppLaunchResult.Launched("Настройки")
                }
            }
            "камера", "фотоаппарат" -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (YouTubeLauncher.startActivityReliably(context, intent)) {
                    return AppLaunchResult.Launched("Камера")
                }
            }
            "контакты", "телефонная книга", "адресная книга" -> {
                val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (YouTubeLauncher.startActivityReliably(context, intent)) {
                    return AppLaunchResult.Launched("Контакты")
                }
            }
            "телефон", "звонилка", "набор номера" -> {
                val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (YouTubeLauncher.startActivityReliably(context, intent)) {
                    return AppLaunchResult.Launched("Телефон")
                }
            }
            "браузер", "интернет" -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ya.ru")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (YouTubeLauncher.startActivityReliably(context, browserIntent)) {
                    return AppLaunchResult.Launched("Браузер")
                }
            }
        }

        val installedApps = queryLaunchableApps(context)
        val match = findBestInstalledApp(norm, installedApps)
        if (match != null) {
            val launchIntent = buildLaunchIntent(context, match)
            if (launchIntent != null) {
                return if (YouTubeLauncher.startActivityReliably(context, launchIntent)) {
                    AppLaunchResult.Launched(match.label)
                } else {
                    AppLaunchResult.Failed("Система заблокировала запуск «${match.label}»")
                }
            }
        }

        return AppLaunchResult.NotFound(rawQuery)
    }

    private fun buildLaunchIntent(context: Context, entry: InstalledAppEntry): Intent? {
        val fromPm = context.packageManager.getLaunchIntentForPackage(entry.packageName)
        if (fromPm != null) {
            fromPm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return fromPm
        }
        if (entry.activityName.isNotBlank()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(entry.packageName, entry.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        return null
    }

    fun openCamera(context: Context, videoMode: Boolean): Boolean {
        val action = if (videoMode) {
            MediaStore.ACTION_VIDEO_CAPTURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (YouTubeLauncher.startActivityReliably(context, intent)) return true
        val fallback = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return YouTubeLauncher.startActivityReliably(context, fallback)
    }

    fun openMaps(context: Context, searchQuery: String? = null): Boolean {
        val uri = if (searchQuery.isNullOrBlank()) {
            Uri.parse("geo:0,0?q=")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(searchQuery.trim())}")
        }
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (YouTubeLauncher.startActivityReliably(context, mapIntent)) return true

        val installedApps = queryLaunchableApps(context)
        val mapApp = findBestInstalledApp("карты", installedApps)
        if (mapApp != null) {
            val launch = buildLaunchIntent(context, mapApp)
            if (launch != null) {
                return YouTubeLauncher.startActivityReliably(context, launch)
            }
        }
        return false
    }

    @Volatile
    private var cachedApps: List<InstalledAppEntry> = emptyList()
    @Volatile
    private var cachedAppsAtMs: Long = 0L
    private const val APP_CACHE_TTL_MS = 45_000L

    fun queryLaunchableApps(context: Context): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        val existing = cachedApps
        if (existing.isNotEmpty() && (now - cachedAppsAtMs) < APP_CACHE_TTL_MS) {
            return existing
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val myPkg = context.packageName
        val loaded = resolveInfos
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val pkg = activityInfo.packageName ?: return@mapNotNull null
                if (pkg == myPkg) return@mapNotNull null

                val activityLabel = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                val appLabel = activityInfo.applicationInfo?.let { pm.getApplicationLabel(it)?.toString()?.trim() }.orEmpty()
                val primaryLabel = activityLabel.ifBlank { appLabel }
                if (primaryLabel.isBlank()) return@mapNotNull null

                InstalledAppEntry(
                    packageName = pkg,
                    label = primaryLabel,
                    activityName = activityInfo.name.orEmpty(),
                    altLabel = if (appLabel.isNotBlank() && !appLabel.equals(primaryLabel, ignoreCase = true)) appLabel else ""
                )
            }
            .distinctBy { "${it.packageName}:${it.label.lowercase()}" }

        cachedApps = loaded
        cachedAppsAtMs = now
        return loaded
    }

    fun findBestInstalledApp(
        spokenQuery: String,
        installedApps: List<InstalledAppEntry>
    ): InstalledAppEntry? {
        val normQuery = cleanAppQuery(RussianVoiceCommandParser.normalize(spokenQuery))
        if (normQuery.isBlank() || installedApps.isEmpty()) return null

        val translitQuery = transliterateRuToEn(normQuery)
        val aliasKeywords = expandAliasKeywords(normQuery)

        var best: InstalledAppEntry? = null
        var bestScore = 0.0

        for (app in installedApps) {
            val scoreMain = scoreCandidate(normQuery, translitQuery, aliasKeywords, app.label, app.packageName)
            val scoreAlt = if (app.altLabel.isNotBlank()) {
                scoreCandidate(normQuery, translitQuery, aliasKeywords, app.altLabel, app.packageName)
            } else {
                0.0
            }
            val score = max(scoreMain, scoreAlt)
            if (score > bestScore && score >= 74.0) {
                bestScore = score
                best = app
            }
        }
        return best
    }

    private fun scoreCandidate(
        normQuery: String,
        translitQuery: String,
        aliasKeywords: List<String>,
        rawLabel: String,
        packageName: String
    ): Double {
        val normLabel = RussianVoiceCommandParser.normalize(rawLabel)
        if (normLabel.isBlank()) return 0.0

        val normPkg = packageName.lowercase()
        val pkgTokens = normPkg.split('.', '_', '-').filter { it.isNotBlank() }
        val labelTokens = normLabel.split(' ').filter { it.isNotBlank() }
        val translitLabel = transliterateRuToEn(normLabel)

        var score = when {
            normLabel == normQuery -> 100.0
            translitLabel == translitQuery -> 97.0
            labelTokens.any { it == normQuery } && normQuery.length >= 3 -> 92.0
            normQuery.length >= 4 && (normLabel.startsWith(normQuery) || (normQuery.startsWith(normLabel) && normLabel.length >= 4)) -> 88.0
            normQuery.length >= 4 && normLabel.contains(normQuery) -> 83.0
            translitQuery.length >= 4 && translitLabel.contains(translitQuery) -> 81.0
            else -> 0.0
        }

        if (score < 95.0 && aliasKeywords.isNotEmpty()) {
            for (kw in aliasKeywords) {
                val kwLower = kw.lowercase()
                when {
                    normLabel == kwLower -> score = max(score, 96.0)
                    labelTokens.any { it == kwLower } -> score = max(score, 90.0)
                    pkgTokens.any { it == kwLower } -> score = max(score, 88.0)
                    kwLower.length >= 5 && (normLabel.contains(kwLower) || normPkg.contains(kwLower)) ->
                        score = max(score, 82.0)
                }
            }
        }

        if (score == 0.0 && normQuery.length >= 4 && normLabel.length >= 4) {
            val simDirect = similarity(normQuery, normLabel)
            val simTranslit = similarity(translitQuery, translitLabel)
            val maxSim = max(simDirect, simTranslit)
            if (maxSim >= 0.78) {
                score = 56.0 + maxSim * 30.0
            }
        }
        return score
    }

    private fun cleanAppQuery(q: String): String {
        var res = q.trim()
        for (prefix in listOf("приложение ", "программу ", "программа ")) {
            if (res.startsWith(prefix)) res = res.removePrefix(prefix).trim()
        }
        for (suffix in listOf(" пожалуйста", " на планшете", " на телефоне")) {
            if (res.endsWith(suffix)) res = res.removeSuffix(suffix).trim()
        }
        return russianCaseOverrides[res] ?: res
    }

    private fun expandAliasKeywords(normQuery: String): List<String> {
        val queryTokens = normQuery.split(' ').filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        for ((triggers, targets) in russianAliasKeywords) {
            val matched = triggers.any { t ->
                when {
                    normQuery == t -> true
                    t.length <= 3 -> queryTokens.any { it == t }
                    t.contains(' ') -> normQuery.contains(t)
                    else -> queryTokens.any { it == t || (it.length >= 5 && t.length >= 5 && (it.contains(t) || t.contains(it))) }
                }
            }
            if (matched) {
                out.addAll(targets)
            }
        }
        return out.distinct()
    }

    fun transliterateRuToEn(input: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )
        return buildString {
            for (ch in input.lowercase()) {
                append(map[ch] ?: ch)
            }
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val dist = levenshtein(a, b)
        val maxLen = max(a.length, b.length).coerceAtLeast(1)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        val m = lhs.length
        val n = rhs.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[n]
    }
}
