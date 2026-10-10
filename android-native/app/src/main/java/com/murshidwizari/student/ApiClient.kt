package com.murshidwizari.student

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** HTTPS API client. Saves only AES-GCM encrypted session cookies, never passwords. */
class ApiClient(context: Context) {
    private val origin = URI("https://www.mur-iq.com/")
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val prefs = context.getSharedPreferences("murshid_session", Context.MODE_PRIVATE)
    private val alias = "murshid_native_session_v1"
    private var remember = false

    init {
        try {
            val saved = prefs.getString("cookies", null)
            if (!saved.isNullOrBlank()) {
                val array = JSONArray(decrypt(saved))
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val value = obj.optString("value")
                    if (name.isBlank() || value.isBlank()) continue
                    cookieManager.cookieStore.add(origin, HttpCookie(name, value).apply {
                        domain = "www.mur-iq.com"; path = "/"; secure = true
                    })
                }
                remember = true
            }
        } catch (_: Exception) {
            prefs.edit().remove("cookies").apply()
            cookieManager.cookieStore.removeAll()
        }
    }

    fun hasRememberedSession(): Boolean = remember && cookieManager.cookieStore.get(origin).isNotEmpty()

    fun setRemember(enabled: Boolean) {
        remember = enabled
        if (enabled) saveCookies() else prefs.edit().remove("cookies").apply()
    }

    fun clear() {
        cookieManager.cookieStore.removeAll()
        remember = false
        prefs.edit().remove("cookies").apply()
    }

    fun call(path: String, payload: JSONObject? = null): JSONObject {
        val safePath = path.trimStart('/')
        if (!safePath.matches(Regex("[a-zA-Z0-9_./?=&%+\\-]+")) || safePath.contains("..") ||
            safePath.contains("//"))
            return JSONObject().put("ok", false).put("message", "المسار غير آمن.")
        val conn = URL(origin.toString() + safePath).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if (payload == null) "GET" else "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Murshid-Client", "AndroidNative")
            conn.setRequestProperty("Cache-Control", "no-store")
            val cookies = cookieManager.cookieStore.get(origin)
            if (cookies.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookies.joinToString("; ") { it.name + "=" + it.value })
            }
            if (payload != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            cookieManager.put(origin, conn.headerFields)
            if (remember) saveCookies()
            if (status in 300..399) {
                return JSONObject().put("ok", false).put("message", "أعاد الخادم توجيه الطلب. تأكد من الاتصال.")
            }
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(3_000_000) } ?: ""
            val response = try { JSONObject(raw) } catch (_: Exception) {
                JSONObject().put("ok", false).put("message", if (status >= 400) "خطأ من الخادم (" + status + ")" else "استجابة غير متوقعة من الخادم.")
            }
            if (status !in 200..299) response.put("ok", false)
            if (status == 401 && safePath != "api/login.php") response.put("session_expired", true)
            return response
        } finally { conn.disconnect() }
    }

    /**
     * Used by PHP endpoints expecting application/x-www-form-urlencoded instead of JSON.
     * Keep this separate from JSON calls to preserve existing iOS-compatible contracts.
     */
    fun postForm(path: String, fields: Map<String, String>): JSONObject {
        val safePath = path.trimStart('/')
        if (!safePath.matches(Regex("[a-zA-Z0-9_./-]+")) || safePath.contains(".."))
            return JSONObject().put("ok", false).put("message", "مسار غير آمن.")
        val conn = URL(origin.toString() + safePath).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("Cache-Control", "no-store")
            val cookies = cookieManager.cookieStore.get(origin)
            if (cookies.isNotEmpty())
                conn.setRequestProperty("Cookie", cookies.joinToString("; ") { it.name + "=" + it.value })
            val encoded = fields.entries.joinToString("&") {
                java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
            }
            conn.outputStream.use { it.write(encoded.toByteArray(StandardCharsets.UTF_8)) }
            val status = conn.responseCode
            cookieManager.put(origin, conn.headerFields)
            if (remember) saveCookies()
            if (status in 300..399) return JSONObject().put("ok", false).put("message", "أعاد الخادم توجيه الطلب.")
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(3_000_000) } ?: ""
            val result = try { JSONObject(raw) } catch (_: Exception) {
                JSONObject().put("ok", false).put("message", "استجابة غير متوقعة من الخادم (" + status + ").")
            }
            if (status !in 200..299) result.put("ok", false)
            if (status == 401) result.put("session_expired", true)
            return result
        } finally { conn.disconnect() }
    }

    /** Upload a bounded JPEG/PNG avatar using the server's real multipart contract. */
    fun uploadAvatar(csrf: String, filename: String, mime: String, bytes: ByteArray): JSONObject {
        if (bytes.isEmpty() || bytes.size > 3_000_000 || mime !in setOf("image/jpeg", "image/png"))
            return JSONObject().put("ok", false).put("message", "اختر صورة JPG أو PNG لا تتجاوز 3 ميغابايت.")
        val boundary = "MurshidNative" + java.util.UUID.randomUUID().toString().replace("-", "")
        val conn = URL(origin.toString() + "mobile/avatar.php").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = false
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            conn.setRequestProperty("Cache-Control", "no-store")
            val cookies = cookieManager.cookieStore.get(origin)
            if (cookies.isNotEmpty())
                conn.setRequestProperty("Cookie", cookies.joinToString("; ") { it.name + "=" + it.value })
            val safeName = filename.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
            conn.outputStream.use { out ->
                fun write(value: String) { out.write(value.toByteArray(StandardCharsets.UTF_8)) }
                write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n" + csrf + "\r\n")
                write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"" + safeName + "\"\r\n")
                write("Content-Type: " + mime + "\r\n\r\n")
                out.write(bytes)
                write("\r\n--" + boundary + "--\r\n")
            }
            val status = conn.responseCode
            cookieManager.put(origin, conn.headerFields)
            if (remember) saveCookies()
            if (status in 300..399)
                return JSONObject().put("ok", false).put("message", "أعاد الخادم توجيه الطلب.")
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(1_000_000) } ?: ""
            val result = try { JSONObject(raw) } catch (_: Exception) {
                JSONObject().put("ok", false).put("message", "استجابة غير متوقعة من الخادم (" + status + ").")
            }
            if (status !in 200..299) result.put("ok", false)
            if (status == 401) result.put("session_expired", true)
            return result
        } finally { conn.disconnect() }
    }

    private data class HtmlReply(val status: Int, val body: String, val location: String?)

    /** Same-origin HTML form transport for the legacy password reset endpoints used on iOS. */
    private fun htmlRequest(path: String, fields: Map<String, String>? = null): HtmlReply {
        if (!path.matches(Regex("[a-zA-Z0-9_./?=&%-]+")) || path.contains("..") || path.contains("//"))
            error("Invalid recovery endpoint")
        val conn = URL(origin.toString() + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if (fields == null) "GET" else "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.setRequestProperty("Cache-Control", "no-store")
            cookieManager.cookieStore.get(origin).takeIf { it.isNotEmpty() }?.let { cookies ->
                conn.setRequestProperty("Cookie", cookies.joinToString("; ") { it.name + "=" + it.value })
            }
            if (fields != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                val body = fields.entries.joinToString("&") {
                    java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
                }
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            cookieManager.put(origin, conn.headerFields)
            if (remember) saveCookies()
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(500_000) } ?: ""
            return HtmlReply(status, body, conn.getHeaderField("Location"))
        } finally { conn.disconnect() }
    }

    private fun readRecoveryRedirect(source: HtmlReply): HtmlReply {
        if (source.status !in 300..399 || source.location.isNullOrBlank()) return source
        val destination = try { origin.resolve(source.location) } catch (_: Exception) { return source }
        if (destination.scheme != "https" || destination.host != origin.host || destination.userInfo != null ||
            destination.port !in setOf(-1, 443)) return source
        val path = destination.rawPath.trimStart('/') +
            (if (destination.rawQuery.isNullOrBlank()) "" else "?" + destination.rawQuery)
        return htmlRequest(path) // At most one GET follow-up; never repeat a POST.
    }

    fun beginPasswordReset(identifier: String): JSONObject {
        if (identifier.isBlank()) return JSONObject().put("ok", false).put("message", "أدخل الهاتف أو البريد المسجل.")
        val csrf = RecoveryRules.csrf(htmlRequest("forgot-password.php").body)
            ?: return JSONObject().put("ok", false).put("message", "تعذر تهيئة التحقق. حاول لاحقاً.")
        val response = htmlRequest("forgot-password.php", mapOf("csrf" to csrf, "identifier" to identifier))
        RecoveryRules.phoneToken(response.location.orEmpty())?.let { token ->
            return JSONObject().put("ok", true).put("channel", "phone").put("token", token)
        }
        val final = readRecoveryRedirect(response)
        val message = RecoveryRules.formMessage(final.body)
        if (message.isError || final.status !in 200..299)
            return JSONObject().put("ok", false)
                .put("message", message.text.ifBlank { "تعذر إكمال الاستعادة. تحقق من البيانات وحاول لاحقاً." })
        return JSONObject().put("ok", true).put("channel", "email")
            .put("message", message.text.ifBlank { "إذا كان البريد مرتبطاً بحساب صالح فسيصلك رابط استعادة آمن." })
    }

    fun resetPhonePassword(token: String, code: String, password: String, confirm: String): JSONObject {
        if (!token.matches(Regex("[a-f0-9]{64}")) || !RecoveryRules.validReset(code, password, confirm))
            return JSONObject().put("ok", false).put("message", "تحقق من رمز WhatsApp وكلمة المرور وتطابق التأكيد.")
        val csrf = RecoveryRules.csrf(htmlRequest("reset-password.php?phone_token=$token").body)
            ?: return JSONObject().put("ok", false).put("message", "انتهت جلسة الاستعادة أو الرابط غير صالح.")
        val response = readRecoveryRedirect(htmlRequest("reset-password.php", mapOf(
            "csrf" to csrf, "phone_token" to token, "action" to "reset",
            "code" to RecoveryRules.normalizedDigits(code), "password" to password, "confirm" to confirm)))
        val status = RecoveryRules.formMessage(response.body)
        if (response.status !in 200..299 || status.isError || !status.text.contains("تم تغيير كلمة المرور"))
            return JSONObject().put("ok", false).put("message", status.text.ifBlank { "لم يؤكد الخادم تغيير كلمة المرور." })
        clear() // All previous login sessions must be reauthenticated after a password change.
        return JSONObject().put("ok", true).put("message", status.text)
    }

    fun resendPhonePasswordCode(token: String): JSONObject {
        if (!token.matches(Regex("[a-f0-9]{64}")))
            return JSONObject().put("ok", false).put("message", "رابط الاستعادة غير صالح.")
        val csrf = RecoveryRules.csrf(htmlRequest("reset-password.php?phone_token=$token").body)
            ?: return JSONObject().put("ok", false).put("message", "تعذر تهيئة إعادة الإرسال.")
        val response = readRecoveryRedirect(htmlRequest("reset-password.php", mapOf(
            "csrf" to csrf, "phone_token" to token, "action" to "resend")))
        val status = RecoveryRules.formMessage(response.body)
        if (response.status !in 200..299 || status.isError)
            return JSONObject().put("ok", false).put("message", status.text.ifBlank { "تعذر إعادة إرسال الرمز." })
        return JSONObject().put("ok", true)
            .put("message", status.text.ifBlank { "تم طلب رمز WhatsApp جديد." })
    }

    private fun saveCookies() {
        try {
            val cookies = JSONArray()
            cookieManager.cookieStore.get(origin).forEach { cookie ->
                if (cookie.name.isNotBlank()) cookies.put(JSONObject().put("name", cookie.name).put("value", cookie.value))
            }
            prefs.edit().putString("cookies", encrypt(cookies.toString())).apply()
        } catch (_: Exception) { /* memory-only fallback */ }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        if (bytes.size < 29) error("Invalid session")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }
}
