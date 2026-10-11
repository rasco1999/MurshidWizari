package com.murshidwizari.student

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

/** Only a local study reminder, never presented as a server push-notification channel. */
object DailyStudyReminder {
    const val requestCode = 704
    private const val alarmAction = "com.murshidwizari.student.STUDY_REMINDER"
    private const val channelId = "daily-study-reminder"
    private const val notificationId = 1900
    private const val preferenceFile = "native_preferences"

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1900,
        Intent(context, StudyReminderReceiver::class.java).setAction(alarmAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun permissionAvailable(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    private fun channel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channelId, "تذكير الدراسة اليومي",
            NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Does not mark the preference enabled unless posting notifications is permitted. */
    fun enable(context: Context, hour: Int, minute: Int): Boolean {
        if (!StudyReminderRules.validTime(hour, minute) || !permissionAvailable(context)) return false
        channel(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
            StudyReminderRules.nextTrigger(System.currentTimeMillis(), hour, minute),
            AlarmManager.INTERVAL_DAY, alarmIntent(context))
        context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE).edit()
            .putBoolean("study_reminder", true)
            .putInt("study_reminder_hour", hour)
            .putInt("study_reminder_minute", minute).apply()
        return true
    }

    fun disable(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(context))
        context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE).edit()
            .putBoolean("study_reminder", false).apply()
    }

    fun restore(context: Context) {
        val preferences = context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        if (preferences.getBoolean("study_reminder", false)) enable(context,
            preferences.getInt("study_reminder_hour", 19),
            preferences.getInt("study_reminder_minute", 0))
    }

    fun notify(context: Context) {
        val preferences = context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        if (!preferences.getBoolean("study_reminder", false) || !permissionAvailable(context)) return
        channel(context)
        val openApp = PendingIntent.getActivity(context, 1900,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_study_notification)
            .setContentTitle("وقت مراجعة قصيرة 📚")
            .setContentText("حتى 10 دقائق اليوم تصنع فرقًا. افتح المرشد وواصل من حيث توقفت.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, notification)
    }

    fun isAlarmAction(action: String?): Boolean = action == alarmAction
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DailyStudyReminder.isAlarmAction(intent.action)) DailyStudyReminder.notify(context)
        else if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED)) DailyStudyReminder.restore(context)
    }
}
