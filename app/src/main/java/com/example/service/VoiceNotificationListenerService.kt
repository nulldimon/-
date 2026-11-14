package com.example.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestampMs: Long,
    val isMessage: Boolean
)

class VoiceNotificationListenerService : NotificationListenerService() {

    companion object {
        private val lock = Any()
        private val recentNotifications = ArrayDeque<CapturedNotification>()
        private const val MAX_ITEMS = 30

        private val messagingPackages = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.viber.voip",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.huawei.message",
            "com.hihonor.mms",
            "ru.ok.android",
            "com.vkontakte.android"
        )

        fun isEnabled(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun getRecentNotifications(limit: Int = 5): List<CapturedNotification> {
            synchronized(lock) {
                return recentNotifications.take(limit)
            }
        }

        fun getRecentMessages(limit: Int = 5): List<CapturedNotification> {
            synchronized(lock) {
                return recentNotifications.filter { it.isMessage }.take(limit)
            }
        }

        fun formatTime(timestampMs: Long): String {
            return SimpleDateFormat("HH:mm", Locale("ru", "RU")).format(Date(timestampMs))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = bigText.ifBlank {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        }
        if (title.isBlank() && body.isBlank()) return

        val appLabel = resolveAppLabel(sbn.packageName)
        val isMessage = notification.category == Notification.CATEGORY_MESSAGE ||
            sbn.packageName in messagingPackages

        val entry = CapturedNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = body,
            timestampMs = sbn.postTime,
            isMessage = isMessage
        )

        synchronized(lock) {
            recentNotifications.removeAll { it.key == entry.key && it.title == entry.title && it.text == entry.text }
            recentNotifications.addFirst(entry)
            while (recentNotifications.size > MAX_ITEMS) {
                recentNotifications.removeLast()
            }
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            when {
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("telegram") -> "Telegram"
                pkg.contains("mms") || pkg.contains("message") -> "Сообщения"
                pkg.contains("phone") || pkg.contains("telecom") -> "Телефон"
                else -> pkg
            }
        }
    }
}
