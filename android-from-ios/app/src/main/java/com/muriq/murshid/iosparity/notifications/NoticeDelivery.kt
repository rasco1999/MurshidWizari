package com.muriq.murshid.iosparity.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.muriq.murshid.iosparity.MainActivity
import com.muriq.murshid.iosparity.data.SchoolAdReadRules
import com.muriq.murshid.iosparity.data.StudentDashboard
import org.json.JSONObject
import com.muriq.murshid.iosparity.net.ApiProblem
import com.muriq.murshid.iosparity.net.MurshidApi
import java.util.concurrent.TimeUnit

/**
 * Local Android alerts for NEW unread server notices.
 * Uses the authenticated session; no notification contents are stored on disk.
 * A different student account invalidates the previous baseline and alerts.
 */
class NoticeDelivery(private val context: Context) {
    companion object {
        const val ACTION_OPEN_NOTICES = "com.muriq.murshid.iosparity.OPEN_NOTICES"
        const val ACTION_OPEN_SCHOOL_ADS = "com.muriq.murshid.iosparity.OPEN_SCHOOL_ADS"
        const val EXTRA_STUDENT_ID = "student_id"
        const val EXTRA_NOTICE_ID = "notice_id"
        private const val PREFS = "murshid_notification_delivery_v1"
        private const val WORK = "student-notification-poll"
        private const val CHANNEL = "student_updates"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val manager = NotificationManagerCompat.from(context)

    fun activeUserId(): Int = prefs.getInt("student", 0)

    fun bind(studentId: Int, allowBackground: Boolean) {
        if (studentId <= 0) return
        if (activeUserId() != studentId) {
            manager.cancelAll()
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            prefs.edit().clear().putInt("student", studentId).apply()
        }
        if (allowBackground) {
            if (!prefs.getBoolean("work_scheduled", false)) {
                val request = PeriodicWorkRequestBuilder<StudentNoticeWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf(EXTRA_STUDENT_ID to studentId))
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK, ExistingPeriodicWorkPolicy.REPLACE, request
                )
                prefs.edit().putBoolean("work_scheduled", true).apply()
            }
        } else if (prefs.getBoolean("work_scheduled", false)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            prefs.edit().putBoolean("work_scheduled", false).apply()
        }
    }

    fun clear() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        manager.cancelAll()
        prefs.edit().clear().apply()
    }

    fun alertsEnabled(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    fun deliver(studentId: Int, inbox: NoticeInbox) {
        if (studentId <= 0 || activeUserId() != studentId) return
        val maxId = inbox.items.maxOfOrNull { it.id } ?: 0
        val previouslySeeded = prefs.contains("last_seen_id")
        val previous = prefs.getInt("last_seen_id", 0)
        // On first sync do not flood the device with older existing notifications.
        if (previouslySeeded && alertsEnabled()) {
            ensureChannel()
            inbox.items.asReversed().asSequence()
                .filter { it.id > previous && !it.read }
                .take(15)
                .forEach { show(studentId, it) }
        }
        // Advance only; server returns 50 most recent entries and IDs are monotonically increasing.
        if (!previouslySeeded || maxId > previous) {
            prefs.edit().putInt("last_seen_id", maxId).apply()
        }
    }

    /**
     * Local fallback for school advertisements on devices with a remembered session:
     * WorkManager refreshes at >=15 minute intervals; Firebase push is still required
     * for timely alerts while Android is dozing. First sync seeds only, with no flood.
     */
    fun deliverSchoolAds(studentId: Int, items: List<JSONObject>) {
        if (studentId <= 0 || activeUserId() != studentId) return
        val revisions = SchoolAdReadRules.published(items)
            .associateBy { SchoolAdReadRules.revision(it) }
        val storageKey = "school_ad_revisions"
        val seeded = prefs.contains(storageKey)
        val prior = prefs.getStringSet(storageKey, emptySet()).orEmpty().toSet()
        if (seeded && alertsEnabled()) {
            ensureChannel()
            revisions.filterKeys { it !in prior }.values.take(5).forEach { ad ->
                val schoolName = ad.optString("school").trim().take(90)
                val adId = ad.optInt("id")
                if (adId <= 0) return@forEach
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_SCHOOL_ADS
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_STUDENT_ID, studentId)
                }
                val pending = PendingIntent.getActivity(
                    context, 200_000 + (adId % 50_000), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("إعلان مدرسة جديد")
                    .setContentText(schoolName.ifBlank { "افتح الإعلانات للاطلاع على التفاصيل." })
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                try {
                    manager.notify("school-$studentId", 200_000 + (adId % 50_000), notification)
                } catch (_: SecurityException) { /* OS permission can be revoked. */ }
            }
        }
        // Snapshot replaces deleted/disabled content, so publishing it again counts as new.
        if (!seeded || revisions.keys != prior) {
            prefs.edit().putStringSet(storageKey, revisions.keys.toSet()).apply()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL, "إشعارات منصة المرشد الوزاري", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "التحديثات المرتبطة بحساب الطالب المسجل" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun show(studentId: Int, notice: StudentNotice) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTICES
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_STUDENT_ID, studentId)
            putExtra(EXTRA_NOTICE_ID, notice.id)
        }
        val pending = PendingIntent.getActivity(
            context, notice.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = notice.body.ifBlank { "لديك إشعار جديد في حسابك." }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notice.title.ifBlank { "المرشد الوزاري" })
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify("student-$studentId", notice.id, notification)
        } catch (_: SecurityException) {
            // Android 13+ permission may have been revoked between check and post.
        }
    }
}

/** Background sync is periodic (>=15 minutes), not instantaneous Firebase push. */
class StudentNoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val delivery = NoticeDelivery(applicationContext)
        val expected = inputData.getInt(NoticeDelivery.EXTRA_STUDENT_ID, 0)
        if (expected <= 0 || delivery.activeUserId() != expected) return Result.success()
        val api = MurshidApi(applicationContext)
        if (!api.hasSavedSession()) return Result.success()
        return try {
            // Reject a stale cookie, switched session or incorrectly bound worker.
            val bootstrap = api.get("mobile/bootstrap.php")
            val remote = bootstrap.optJSONObject("user")?.optInt("id") ?: 0
            if (remote != expected) {
                delivery.clear()
                Result.success()
            } else {
                delivery.deliverSchoolAds(expected, StudentDashboard.from(bootstrap).ads)
                val inbox = NoticeInbox.from(api.get("mobile/notifications.php"))
                delivery.deliver(expected, inbox)
                Result.success()
            }
        } catch (error: ApiProblem) {
            if (error.sessionExpired || error.code == 401) {
                delivery.clear()
                Result.success()
            } else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
