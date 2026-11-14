package com.example.control

import android.content.Context
import com.example.voice.VoiceFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

data class ActiveReminder(
    val id: Long,
    val title: String,
    val triggerAtMs: Long,
    val timeLabel: String,
    val job: Job
)

object ReminderManager {
    private var nextId = 1L
    private val activeReminders = mutableListOf<ActiveReminder>()

    fun scheduleReminder(
        context: Context,
        feedbackManager: VoiceFeedbackManager,
        scope: CoroutineScope,
        title: String,
        hour: Int?,
        minute: Int?,
        inMinutes: Int?
    ): String {
        val now = Calendar.getInstance()
        val delayMs: Long
        val timeLabel: String

        if (inMinutes != null) {
            val mins = inMinutes.coerceIn(1, 720)
            delayMs = mins * 60_000L
            val target = Calendar.getInstance().apply { add(Calendar.MINUTE, mins) }
            timeLabel = String.format("%02d:%02d", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
            PhoneAndDeviceController.setTimer(context, mins, title)
        } else {
            val targetHour = hour ?: ((now.get(Calendar.HOUR_OF_DAY) + 1) % 24)
            val targetMinute = minute ?: 0
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            delayMs = (targetCal.timeInMillis - now.timeInMillis).coerceAtLeast(10_000L)
            timeLabel = String.format("%02d:%02d", targetHour, targetMinute)
            PhoneAndDeviceController.setAlarm(context, targetHour, targetMinute, title)
        }

        val id = nextId++
        val job = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            synchronized(activeReminders) {
                activeReminders.removeAll { it.id == id }
            }
            feedbackManager.vibrateSuccess()
            feedbackManager.speak("Напоминание: $title!")
        }

        synchronized(activeReminders) {
            activeReminders.add(
                ActiveReminder(
                    id = id,
                    title = title,
                    triggerAtMs = System.currentTimeMillis() + delayMs,
                    timeLabel = timeLabel,
                    job = job
                )
            )
        }
        return "Напоминание «$title» поставлено на $timeLabel"
    }

    fun listRemindersSummary(): String {
        synchronized(activeReminders) {
            val upcoming = activeReminders.filter { it.triggerAtMs > System.currentTimeMillis() }
            if (upcoming.isEmpty()) {
                return "Активных напоминаний сейчас нет"
            }
            val listText = upcoming.joinToString(". ") { "${it.title} в ${it.timeLabel}" }
            return "У вас напоминаний: ${upcoming.size}. $listText"
        }
    }

    fun cancelAllReminders(): String {
        synchronized(activeReminders) {
            if (activeReminders.isEmpty()) {
                return "Активных напоминаний нет"
            }
            val count = activeReminders.size
            activeReminders.forEach { it.job.cancel() }
            activeReminders.clear()
            return "Отменено напоминаний: $count"
        }
    }
}
