package com.example.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.voice.RussianVoiceCommandParser
import kotlin.math.max
import kotlin.math.min

sealed class WhatsAppActionResult {
    data class OpenedApp(val packageLabel: String) : WhatsAppActionResult()
    data class CallingContact(val displayName: String, val usedVoipMime: Boolean) : WhatsAppActionResult()
    data class ContactNotFound(val query: String) : WhatsAppActionResult()
    data object MissingContactsPermission : WhatsAppActionResult()
    data object WhatsAppNotInstalled : WhatsAppActionResult()
    data class Failed(val reason: String) : WhatsAppActionResult()
}

data class ContactMatchCandidate(
    val displayName: String,
    val voipDataId: Long?,
    val profileDataId: Long?,
    val phoneNumber: String?
)

object WhatsAppContactCaller {

    private const val WHATSAPP_PKG = "com.whatsapp"
    private const val WHATSAPP_BIZ_PKG = "com.whatsapp.w4b"
    private const val MIME_WA_VOIP_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private const val MIME_WA_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"

    fun findInstalledWhatsAppPackage(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        val candidates = listOf(WHATSAPP_PKG to "WhatsApp", WHATSAPP_BIZ_PKG to "WhatsApp Business")
        for ((pkg, label) in candidates) {
            try {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                return pkg to label
            } catch (_: Throwable) {
                try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                    return pkg to label
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    fun openWhatsApp(context: Context): WhatsAppActionResult {
        val installed = findInstalledWhatsAppPackage(context)
            ?: return WhatsAppActionResult.WhatsAppNotInstalled

        val launchIntent = context.packageManager.getLaunchIntentForPackage(installed.first)
            ?: return WhatsAppActionResult.Failed("Не удалось запустить ${installed.second}")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return if (YouTubeLauncher.startActivityReliably(context, launchIntent)) {
            WhatsAppActionResult.OpenedApp(installed.second)
        } else {
            WhatsAppActionResult.Failed("Система заблокировала запуск WhatsApp")
        }
    }

    fun callContact(context: Context, spokenContactName: String): WhatsAppActionResult {
        val installed = findInstalledWhatsAppPackage(context)
            ?: return WhatsAppActionResult.WhatsAppNotInstalled

        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) {
            return WhatsAppActionResult.MissingContactsPermission
        }

        val candidates = loadContacts(context)
        val bestMatch = findBestContactMatch(spokenContactName, candidates)
            ?: return WhatsAppActionResult.ContactNotFound(spokenContactName)

        // 1. Best path: direct WhatsApp VoIP call MIME data URI
        if (bestMatch.voipDataId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, bestMatch.voipDataId.toString()),
                    MIME_WA_VOIP_CALL
                )
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(bestMatch.displayName, usedVoipMime = true)
            }
        }

        // 2. Fallback: WhatsApp profile chat MIME data URI (Accessibility helper can tap Audio Call)
        if (bestMatch.profileDataId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, bestMatch.profileDataId.toString()),
                    MIME_WA_PROFILE
                )
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(bestMatch.displayName, usedVoipMime = false)
            }
        }

        // 3. Fallback: phone number via wa.me / whatsapp send URI
        val digits = bestMatch.phoneNumber?.filter { it.isDigit() }.orEmpty()
        if (digits.length >= 7) {
            val normalizedPhone = if (digits.length == 11 && digits.startsWith("8")) {
                "7" + digits.substring(1)
            } else {
                digits
            }
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone")
            ).apply {
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(bestMatch.displayName, usedVoipMime = false)
            }
        }

        return WhatsAppActionResult.ContactNotFound(spokenContactName)
    }

    private fun loadContacts(context: Context): List<ContactMatchCandidate> {
        val byName = linkedMapOf<String, ContactMatchCandidate>()
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            MIME_WA_VOIP_CALL,
            MIME_WA_PROFILE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        )

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
            val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val dataId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                val displayName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                if (displayName.isBlank()) continue
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                val phone = if (phoneIdx >= 0) cursor.getString(phoneIdx) else null

                val key = displayName.lowercase()
                val existing = byName[key] ?: ContactMatchCandidate(displayName, null, null, null)
                byName[key] = when (mime) {
                    MIME_WA_VOIP_CALL -> existing.copy(voipDataId = existing.voipDataId ?: dataId)
                    MIME_WA_PROFILE -> existing.copy(profileDataId = existing.profileDataId ?: dataId)
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        existing.copy(phoneNumber = existing.phoneNumber ?: phone)
                    else -> existing
                }
            }
        }
        return byName.values.toList()
    }

    /**
     * Returns up to [limit] strong matches sorted descending by score so ambiguous names can be confirmed.
     */
    fun findMatchingContactCandidates(
        context: Context,
        spokenQuery: String,
        limit: Int = 3
    ): List<ContactMatchCandidate> {
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return emptyList()
        return rankCandidates(spokenQuery, loadContacts(context)).take(limit).map { it.first }
    }

    fun startWhatsAppVideoCall(context: Context, candidate: ContactMatchCandidate): WhatsAppActionResult {
        val installed = findInstalledWhatsAppPackage(context)
            ?: return WhatsAppActionResult.WhatsAppNotInstalled

        if (candidate.profileDataId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, candidate.profileDataId.toString()),
                    MIME_WA_PROFILE
                )
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(candidate.displayName, usedVoipMime = false)
            }
        }
        val digits = candidate.phoneNumber?.filter { it.isDigit() }.orEmpty()
        if (digits.length >= 7) {
            val normalizedPhone = if (digits.length == 11 && digits.startsWith("8")) "7" + digits.substring(1) else digits
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone")
            ).apply {
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(candidate.displayName, usedVoipMime = false)
            }
        }
        return WhatsAppActionResult.Failed("Не удалось открыть чат WhatsApp для ${candidate.displayName}")
    }

    /**
     * Matches Russian inflected contact names (e.g. "маме" -> "Мама", "ивану" -> "Иван Петров")
     * with priority for contacts that have a WhatsApp VoIP entry.
     */
    fun findBestContactMatch(
        spokenQuery: String,
        candidates: List<ContactMatchCandidate>
    ): ContactMatchCandidate? {
        return rankCandidates(spokenQuery, candidates).firstOrNull()?.first
    }

    fun rankCandidates(
        spokenQuery: String,
        candidates: List<ContactMatchCandidate>
    ): List<Pair<ContactMatchCandidate, Double>> {
        val normQuery = RussianVoiceCommandParser.normalize(spokenQuery)
        if (normQuery.isBlank() || candidates.isEmpty()) return emptyList()
        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }

        val scored = mutableListOf<Pair<ContactMatchCandidate, Double>>()

        for (candidate in candidates) {
            val normName = RussianVoiceCommandParser.normalize(candidate.displayName)
            if (normName.isBlank()) continue

            var score = when {
                normName == normQuery -> 100.0
                normName.startsWith(normQuery) || normQuery.startsWith(normName) -> 85.0
                normName.contains(normQuery) -> 75.0
                else -> 0.0
            }

            if (score == 0.0) {
                val nameTokens = normName.split(" ").filter { it.isNotBlank() }
                var tokenMatches = 0
                var maxTokenSim = 0.0
                for (qt in queryTokens) {
                    for (nt in nameTokens) {
                        val sim = russianNameTokenSimilarity(qt, nt)
                        if (sim > maxTokenSim) maxTokenSim = sim
                        if (sim >= 0.72) tokenMatches++
                    }
                }
                if (tokenMatches > 0) {
                    score = 50.0 + (tokenMatches * 10.0) + (maxTokenSim * 15.0)
                }
            }

            if (score > 0.0 && candidate.voipDataId != null) {
                score += 8.0 // Prefer WhatsApp-registered contacts
            }

            if (score >= 55.0) {
                scored.add(candidate to score)
            }
        }

        return scored.sortedByDescending { it.second }
    }

    private fun russianNameTokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val stemA = stripRussianEnding(a)
        val stemB = stripRussianEnding(b)
        if (stemA.length >= 3 && stemA == stemB) return 0.95
        if (stemA.length >= 3 && (stemB.startsWith(stemA) || stemA.startsWith(stemB))) return 0.85

        val dist = levenshtein(a, b)
        val maxLen = max(a.length, b.length).coerceAtLeast(1)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun stripRussianEnding(word: String): String {
        var w = word.trimEnd('ь')
        if (w.startsWith("дочер")) return "доч"
        if (w.startsWith("дочк")) return "доч"
        if (w.startsWith("матер")) return "мат"
        if (w.length <= 3) return w
        val endings2 = listOf("ой", "ей", "ом", "ем", "ам", "ям", "ах", "ях", "ую", "юю")
        for (e in endings2) {
            if (w.length > 4 && w.endsWith(e)) return w.dropLast(2).trimEnd('ь')
        }
        val endings1 = setOf('а', 'я', 'у', 'ю', 'е', 'и', 'ы', 'о')
        if (w.last() in endings1) {
            w = w.dropLast(1).trimEnd('ь')
            if (w.startsWith("дочер")) return "доч"
            if (w.startsWith("дочк")) return "доч"
            if (w.startsWith("матер")) return "мат"
            return w
        }
        return w
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

    fun resolveContact(context: Context, spokenContactName: String): ContactMatchCandidate? {
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return null
        val candidates = loadContacts(context)
        return findBestContactMatch(spokenContactName, candidates)
    }

    fun sendMessageToContact(
        context: Context,
        spokenContactName: String,
        messageText: String
    ): WhatsAppActionResult {
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) {
            return WhatsAppActionResult.MissingContactsPermission
        }
        val candidates = loadContacts(context)
        val bestMatch = findBestContactMatch(spokenContactName, candidates)
            ?: return WhatsAppActionResult.ContactNotFound(spokenContactName)

        val digits = bestMatch.phoneNumber?.filter { it.isDigit() }.orEmpty()
        if (digits.length < 7) {
            return WhatsAppActionResult.Failed("У контакта ${bestMatch.displayName} нет номера телефона")
        }
        val normalizedPhone = if (digits.length == 11 && digits.startsWith("8")) {
            "7" + digits.substring(1)
        } else {
            digits
        }

        val installed = findInstalledWhatsAppPackage(context)
        if (installed != null) {
            val encodedText = java.net.URLEncoder.encode(messageText.trim(), "UTF-8")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText")
            ).apply {
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (YouTubeLauncher.startActivityReliably(context, intent)) {
                return WhatsAppActionResult.CallingContact(bestMatch.displayName, usedVoipMime = false)
            }
        }

        // Fallback to system SMS compose with prefilled body
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$normalizedPhone")).apply {
            putExtra("sms_body", messageText.trim())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (YouTubeLauncher.startActivityReliably(context, smsIntent)) {
            WhatsAppActionResult.CallingContact(bestMatch.displayName, usedVoipMime = false)
        } else {
            WhatsAppActionResult.Failed("Не удалось открыть WhatsApp или SMS")
        }
    }
}
