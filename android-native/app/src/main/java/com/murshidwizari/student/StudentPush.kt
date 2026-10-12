package com.murshidwizari.student

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class MurshidApplication : Application() {
    override fun onCreate() { super.onCreate(); StudentPush.initialize(this) }
}

class StudentMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { StudentPush.tokenChanged(this, token) }
    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only signals; never render a remote title, HTML, URL or student detail.
        StudentPush.receive(this, message.data)
    }
}

object StudentPush {
    const val requestCode = 1902
    const val inboxExtra = "murshid_push_binding"
    private const val channel = "student_account_updates"
    private const val noticeId = 1902
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutex = Any()
    private lateinit var appContext: Context
    @Volatile private var activeApi: ApiClient? = null
    @Volatile private var activeCsrf = ""
    @Volatile private var generation = 0
    @Volatile private var syncing = false
    @Volatile private var lastSync = 0L
    private fun prefs(context: Context) = context.getSharedPreferences("student_push", Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun configured(context: Context): Boolean = prefs(context).getBoolean("configured", false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "إشعارات الحساب", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "تنبيهات جديدة من المرشد الوزاري"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        val raw = prefs(context).getString("firebase_config", null) ?: return
        runCatching { initializeFirebase(JSONObject(raw)) }
    }

    private fun initializeFirebase(config: JSONObject): FirebaseMessaging {
        if (runCatching { FirebaseApp.getInstance() }.getOrNull() == null) FirebaseApp.initializeApp(
            appContext, FirebaseOptions.Builder()
                .setApplicationId(config.getString("app_id")).setApiKey(config.getString("api_key"))
                .setGcmSenderId(config.getString("sender_id")).setProjectId(config.getString("project_id")).build())
        return FirebaseMessaging.getInstance().also { it.isAutoInitEnabled = false }
    }

    /** Runs only after the backend authenticates the account. Passwords/CSRF are never persisted here. */
    fun attach(context: Context, api: ApiClient, userId: Int, csrf: String, ready: (Boolean) -> Unit = {}) {
        if (userId <= 0 || csrf.isBlank()) return
        val context = context.applicationContext
        synchronized(mutex) {
            val p = prefs(context)
            if (p.getInt("user_id", 0) != userId || !PushRules.validBinding(p.getString("binding", "").orEmpty())) {
                generation++; lastSync = 0
                p.edit().putInt("user_id", userId).putString("binding", UUID.randomUUID().toString())
                    .remove("last_seen").remove("registered_token").commit()
                context.getSystemService(NotificationManager::class.java).cancel(noticeId)
            }
            if (!p.contains("installation")) p.edit().putString("installation", UUID.randomUUID().toString()).commit()
            activeApi = api; activeCsrf = csrf
            if (syncing || System.currentTimeMillis() - lastSync < 60_000) {
                main.post { ready(configured(context)) }; return
            }
            syncing = true; lastSync = System.currentTimeMillis()
        }
        val epoch = generation
        worker.execute {
            try {
                val response = api.call("mobile/android-push-config.php")
                if (epoch != generation) return@execute
                val config = response.optJSONObject("firebase")
                var enabled = response.optBoolean("ok") && response.optBoolean("enabled") && config != null
                if (enabled && config != null) {
                    enabled = config.optString("package_name") == "com.murshidwizari.student" &&
                        config.optString("app_id").matches(Regex("1:[0-9]+:android:[a-fA-F0-9]+")) &&
                        config.optString("sender_id").matches(Regex("[0-9]+")) &&
                        config.optString("project_id").matches(Regex("[a-z0-9][a-z0-9-]{4,62}")) &&
                        config.optString("api_key").matches(Regex("AIza[A-Za-z0-9_-]{35}"))
                    if (enabled) {
                        prefs(context).edit().putString("firebase_config", config.toString()).apply()
                        if (allowed(context)) initializeFirebase(config).token.addOnSuccessListener { token ->
                            if (epoch == generation) tokenChanged(context, token)
                        }
                    }
                }
                prefs(context).edit().putBoolean("configured", enabled).apply()
                main.post { if (epoch == generation) ready(enabled) }
            } catch (_: Exception) {
                // Offline or not deployed yet: the authenticated inbox stays usable.
            } finally { syncing = false }
        }
    }

    fun refresh(context: Context) {
        lastSync = 0
        val api = activeApi ?: return
        attach(context, api, prefs(context).getInt("user_id", 0), activeCsrf)
    }

    fun tokenChanged(context: Context, token: String) {
        if (token.isBlank() || token.length > 4096) return
        val context = context.applicationContext
        prefs(context).edit().putString("token", token).apply()
        val epoch = generation
        worker.execute {
            val api = activeApi ?: return@execute
            val p = prefs(context)
            val binding = p.getString("binding", "").orEmpty()
            if (epoch != generation || !allowed(context) || !PushRules.validBinding(binding) || activeCsrf.isBlank()) return@execute
            if (p.getString("registered_token", null) == token &&
                System.currentTimeMillis() - p.getLong("registered_at", 0) in 0 until 86_400_000L) return@execute
            try {
                val result = api.call("mobile/fcm-register.php", JSONObject().put("csrf", activeCsrf)
                    .put("action", "register").put("token", token).put("binding_id", binding)
                    .put("installation_id", p.getString("installation", "")))
                if (epoch == generation && result.optBoolean("ok"))
                    p.edit().putString("registered_token", token).putLong("registered_at", System.currentTimeMillis()).apply()
            } catch (_: Exception) { /* Next resume retries; token is never logged. */ }
        }
    }

    /** The server revokes the installation together with a successful logout. */
    fun logoutPayload(context: Context, csrf: String): JSONObject = JSONObject().put("csrf", csrf)
        .put("android_push_installation_id", prefs(context).getString("installation", ""))
        .put("android_push_binding_id", prefs(context).getString("binding", ""))

    fun detach(context: Context) {
        synchronized(mutex) {
            generation++; activeApi = null; activeCsrf = ""; lastSync = 0
            prefs(context).edit().remove("user_id").remove("binding").remove("last_seen")
                .remove("registered_token").commit()
        }
        context.getSystemService(NotificationManager::class.java).cancel(noticeId)
        runCatching { FirebaseMessaging.getInstance().isAutoInitEnabled = false }
    }

    fun matchesInboxIntent(context: Context, intent: Intent, userId: Int): Boolean {
        val binding = intent.getStringExtra(inboxExtra) ?: return false
        val p = prefs(context)
        return userId > 0 && userId == p.getInt("user_id", 0) && PushRules.validBinding(binding) && binding == p.getString("binding", "")
    }

    fun receive(context: Context, data: Map<String, String>) = synchronized(mutex) {
        val p = prefs(context)
        val binding = data["binding_id"].orEmpty()
        val id = data["notification_id"]?.toLongOrNull() ?: return@synchronized
        if (!PushRules.accepts(p.getInt("user_id", 0), p.getString("binding", "").orEmpty(),
                binding, id, p.getLong("last_seen", 0), allowed(context))) return@synchronized
        val intent = Intent(context, MainActivity::class.java).putExtra(inboxExtra, binding)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, noticeId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_study_notification).setContentTitle("المرشد الوزاري")
            .setContentText("لديك إشعار جديد. افتح التطبيق للاطلاع عليه.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(noticeId, notification)
            p.edit().putLong("last_seen", id).apply()
        } catch (_: SecurityException) { /* Permission may change between the check and notify. */ }
    }
}
