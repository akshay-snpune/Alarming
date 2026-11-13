package com.alarming.feature.reminders

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.alarming.MainActivity
import com.alarming.R
import kotlinx.coroutines.*

class ReminderAlarms(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun notificationPermissionMissing() =
        Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun notificationsAllowed() = !notificationPermissionMissing() && notifications.areNotificationsEnabled()

    private fun alarmIntent(id: Long, trigger: Long = 0) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("com.stagebell.REMIND")
            .setData(Uri.parse("stagebell://reminder/$id"))
            .putExtra("id", id).putExtra("trigger", trigger),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(record: ReminderRecord) {
        val trigger = record.nextTrigger ?: return
        if (!record.enabled || record.completed) return
        val pending = alarmIntent(record.id, trigger)
        val at = maxOf(trigger, System.currentTimeMillis() + 1000)
        try {
            if (exactAvailable()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancelAlarm(id: Long) { manager.cancel(alarmIntent(id)) }
    fun cancel(id: Long) {
        cancelAlarm(id)
        notifications.cancel(id.toInt())
    }

    suspend fun notify(record: ReminderRecord) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val library = com.alarming.audio.ToneLibrary(context)
        val uri = library.uri(record.sound)
        val name = library.name(record.sound)
        val channelId = "reminder_${if (uri == null) "silent" else record.sound.replace(' ', '_')}_${record.vibration}"
        val channel = NotificationChannel(channelId, "$name reminders${if (record.vibration) " with vibration" else ""}", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        channel.enableVibration(record.vibration)
        notifications.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("openReminders", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderReceiver::class.java).setAction("com.stagebell.DONE")
                .setData(Uri.parse("stagebell://done/${record.id}" )).putExtra("id", record.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(record.title).setContentText(record.description.ifBlank { "Your reminder is due." })
            .setStyle(Notification.BigTextStyle().bigText(record.description.ifBlank { "Your reminder is due." }))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Done", done).build()).build()
        notifications.notify(record.id.toInt(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val store = ReminderStore.get(context)
                    when (intent.action) {
                        "com.stagebell.REMIND" -> store.deliver(intent.getLongExtra("id", 0), intent.getLongExtra("trigger", 0))
                        "com.stagebell.DONE" -> store.complete(intent.getLongExtra("id", 0))
                        else -> store.recover()
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("StageBellReminders", "Reminder delivery or recovery failed", error)
            } finally { pending.finish() }
        }
    }
}