package com.wkzxmwk.fooddiary.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.wkzxmwk.fooddiary.FoodDiaryApplication
import com.wkzxmwk.fooddiary.MainActivity
import com.wkzxmwk.fooddiary.R
import com.wkzxmwk.fooddiary.domain.model.AppSettings
import com.wkzxmwk.fooddiary.domain.model.ReminderType
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun applySettings(settings: AppSettings) {
        if (!settings.remindersEnabled) {
            ReminderType.entries.forEach(::cancel)
            return
        }
        ReminderType.entries.forEach { type -> schedule(type, settings.reminderTime(type)) }
    }

    fun schedule(type: ReminderType, time: LocalTime) {
        val now = ZonedDateTime.now()
        var trigger = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.toInstant().toEpochMilli(),
            reminderPendingIntent(type),
        )
    }

    private fun cancel(type: ReminderType) {
        alarmManager.cancel(reminderPendingIntent(type))
    }

    private fun reminderPendingIntent(type: ReminderType): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + type.ordinal,
            Intent(context, ReminderReceiver::class.java)
                .setAction("$ACTION_REMINDER.${type.name}")
                .putExtra(EXTRA_TYPE, type.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val EXTRA_TYPE = "reminder_type"
        private const val ACTION_REMINDER = "com.wkzxmwk.fooddiary.action.REMINDER"
        private const val REQUEST_CODE_BASE = 8_100
        const val CHANNEL_ID = "daily_record_reminders"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)
            ?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() }
            ?: return
        showReminderNotification(context, type)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as FoodDiaryApplication
                val settings = application.container.settingsRepository.settings.first()
                if (settings.remindersEnabled) {
                    application.container.reminderScheduler.schedule(type, settings.reminderTime(type))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as FoodDiaryApplication
                val settings = application.container.settingsRepository.settings.first()
                application.container.reminderScheduler.applySettings(settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun showReminderNotification(context: Context, type: ReminderType) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                "饮食记录提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "按设定时间提醒记录饮食和体重"
            },
        )
    }
    val openApp = PendingIntent.getActivity(
        context,
        9_000,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(type.displayName)
        .setContentText(type.notificationText)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .setCategory(Notification.CATEGORY_REMINDER)
        .build()
    manager.notify(10_100 + type.ordinal, notification)
}
