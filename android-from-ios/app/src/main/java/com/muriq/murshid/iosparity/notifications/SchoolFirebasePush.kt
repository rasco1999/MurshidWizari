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
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muriq.murshid.iosparity.MainActivity
import com.muriq.murshid.iosparity.R
import com.muriq.murshid.iosparity.net.MurshidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Optional first-party FCM channel. No Google service-account key ever ships in the APK.
 * It remains disabled until /mobile/android-push-config.php is deployed on the server.
 * Existing WorkManager announcements and normal in-app unread indicators remain intact.
 */
object SchoolFirebasePush {
    private const val PREFERENCES = "murshid_native_fcm_v1"
    private const val CHANNEL = "murshid_school_fcm"
    private const val PACKAGE_NAME = "com.muriq.murshid.iosparity"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var currentApi: MurshidApi? = null
    @Volatile private var currentCsrf = ""
    @Volatile private var epoch = 0
    private val mutex = Any()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Reinitialize the public Firebase client from protected app preferences at cold start. */
    fun initialize(ctx: Context) {
        val app = ctx.applicationContext
        val stored = prefs(app).getString("firebase_config", null) ?: return
        runCatching {
            val config = JSONObject(stored)
            if (validPublicConfig(config)) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(config.getString("app_id"))
                    .setApiKey(config.getString("api_key"))
                    .setGcmSenderId(config.getString("sender_id"))
                    .setProjectId(config.getString("project_id"))
                    .build()
                if (FirebaseApp.getApps(app).none { it.name == "[DEFAULT]" }) {
                    FirebaseApp.initializeApp(app, options)
                }
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
            }
        }
    }

    fun acceptedBinding(storedUser: Int, storedBinding: String, incomingBinding: String,
                        lastId: Long, currentId: Long): Boolean =
        storedUser > 0 && isUuid(storedBinding) && storedBinding == incomingBinding &&
            currentId > 0 && currentId > lastId

    private fun isUuid(value: String) =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    fun attach(ctx: Context, api: MurshidApi, userId: Int, csrf: String) {
        if (userId <= 0 || csrf.isBlank()) return
        val app = ctx.applicationContext
        val serial: Int
        synchronized(mutex) {
            val p = prefs(app)
            if (p.getInt("user_id", 0) != userId || !isUuid(p.getString("binding", "").orEmpty())) {
                epoch++
                p.edit().putInt("user_id", userId).putString("binding", UUID.randomUUID().toString())
                    .remove("registered_token").remove("last_seen_id").apply()
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
            }
            if (!p.contains("installation")) p.edit().putString("installation", UUID.randomUUID().toString()).apply()
            currentApi = api
            currentCsrf = csrf
            serial = epoch
        }
        scope.launch {
            try {
                val response = api.get("mobile/android-push-config.php")
                if (serial != epoch || !response.optBoolean("enabled")) return@launch
                val firebase = response.optJSONObject("firebase") ?: return@launch
                if (!validPublicConfig(firebase)) return@launch
                prefs(app).edit().putString("firebase_config", firebase.toString()).apply()
                val options = FirebaseOptions.Builder()
                    .setApplicationId(firebase.getString("app_id"))
                    .setApiKey(firebase.getString("api_key"))
                    .setGcmSenderId(firebase.getString("sender_id"))
                    .setProjectId(firebase.getString("project_id"))
                    .build()
                val configured = synchronized(mutex) {
                    if (serial != epoch) false
                    else {
                        val existing = FirebaseApp.getApps(app).firstOrNull { it.name == "[DEFAULT]" }
                        if (existing == null) FirebaseApp.initializeApp(app, options)
                        true
                    }
                }
                if (!configured) return@launch
                val messaging = FirebaseMessaging.getInstance()
                messaging.isAutoInitEnabled = false
                messaging.token.addOnSuccessListener { newToken ->
                    tokenChanged(app, newToken)
                }
            } catch (_: Exception) {
                // Missing server FCM configuration never interrupts the student's lessons.
            }
        }
    }

    private fun validPublicConfig(j: JSONObject): Boolean {
        val sender = j.optString("sender_id")
        return j.optString("package_name") == PACKAGE_NAME &&
            j.optString("app_id").matches(Regex("1:[0-9]+:android:[0-9a-fA-F]+")) &&
            sender.matches(Regex("[0-9]+")) &&
            j.optString("project_id").matches(Regex("[a-z0-9][a-z0-9-]{4,62}")) &&
            j.optString("api_key").matches(Regex("AIza[A-Za-z0-9_-]{35}"))
    }

    fun tokenChanged(ctx: Context, token: String) {
        if (token.length !in 20..4096 || !token.matches(Regex("[A-Za-z0-9_:.-]+"))) return
        val app = ctx.applicationContext
        val generation: Int
        synchronized(mutex) {
            prefs(app).edit().putString("token", token).apply()
            generation = epoch
        }
        scope.launch {
            val api = currentApi ?: return@launch
            val p = prefs(app)
            val csrf = currentCsrf
            val binding = p.getString("binding", "").orEmpty()
            val installation = p.getString("installation", "").orEmpty()
            if (generation != epoch || csrf.isEmpty() || !isUuid(binding) || !isUuid(installation)) return@launch
            if (p.getString("registered_token", null) == token) return@launch
            try {
                val payload = JSONObject().put("csrf", csrf).put("action", "register")
                    .put("token", token).put("binding_id", binding).put("installation_id", installation)
                val reply = api.post("mobile/fcm-register.php", payload)
                if (generation == epoch && reply.optBoolean("ok"))
                    p.edit().putString("registered_token", token).apply()
            } catch (_: Exception) { /* Re-register on the next authenticated attach. */ }
        }
    }

    fun logoutPayload(ctx: Context, csrf: String): JSONObject {
        val p = prefs(ctx)
        return JSONObject().put("csrf", csrf)
            .put("android_push_installation_id", p.getString("installation", ""))
            .put("android_push_binding_id", p.getString("binding", ""))
    }

    fun detach(ctx: Context) {
        synchronized(mutex) {
            epoch++
            currentApi = null
            currentCsrf = ""
            prefs(ctx).edit().remove("user_id").remove("binding").remove("token")
                .remove("registered_token").remove("last_seen_id").apply()
        }
        runCatching { FirebaseMessaging.getInstance().isAutoInitEnabled = false }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    fun incoming(ctx: Context, data: Map<String, String>) {
        val app = ctx.applicationContext
        val p = prefs(app)
        val id = data["notification_id"]?.toLongOrNull() ?: return
        val expected = p.getString("binding", "").orEmpty()
        if (!acceptedBinding(p.getInt("user_id", 0), expected, data["binding_id"].orEmpty(),
                p.getLong("last_seen_id", 0L), id)) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val school = data["type"] == "school_ad"
        val intent = Intent(app, MainActivity::class.java).apply {
            action = if (school) NoticeDelivery.ACTION_OPEN_SCHOOL_ADS else NoticeDelivery.ACTION_OPEN_NOTICES
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NoticeDelivery.EXTRA_STUDENT_ID, p.getInt("user_id", 0))
            if (!school) putExtra(NoticeDelivery.EXTRA_NOTICE_ID, id.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "إعلانات وإشعارات المرشد",
            NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(app, 8120, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_murshid_status)
            .setContentTitle(if (school) "إعلان مدرسة جديد" else "إشعار جديد من المرشد الوزاري")
            .setContentText("افتح التطبيق للاطلاع على التفاصيل.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
        manager.notify(8120, notification)
        p.edit().putLong("last_seen_id", id).apply()
    }
}

class SchoolFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SchoolFirebasePush.tokenChanged(this, token)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        // Reject externally supplied title/body/link; binding validation occurs before display.
        SchoolFirebasePush.incoming(this, message.data)
    }
}
