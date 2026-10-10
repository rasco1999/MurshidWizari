package com.muriq.murshid.iosparity.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
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

class ApiProblem(
    override val message: String,
    val code: Int = 0,
    val needsSubscription: Boolean = false,
    val sessionExpired: Boolean = false
) : Exception(message)

/**
 * Native Android counterpart of patches/APIClient.swift from the iOS source.
 * Uses the same official PHP JSON endpoints. No embedded browser or WebView.
 * Reads may be retried safely; login, answers, payments and all POSTs never are.
 */
class MurshidApi(private val context: Context) {
    private val origin = URI.create("https://www.mur-iq.com/")
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val preferences = context.getSharedPreferences("murshid_native_session_v2", Context.MODE_PRIVATE)
    private val vault = SessionVault()
    private var remember = false

    init {
        val encrypted = preferences.getString("session", null)
        if (!encrypted.isNullOrBlank()) {
            try {
                val decoded = JSONArray(vault.decrypt(encrypted))
                for (i in 0 until decoded.length()) {
                    val row = decoded.optJSONObject(i) ?: continue
                    val name = row.optString("name")
                    val value = row.optString("value")
                    if (name.isBlank() || value.isBlank()) continue
                    cookies.cookieStore.add(origin, HttpCookie(name, value).apply {
                        domain = "www.mur-iq.com"; path = "/"; secure = true
                    })
                }
                remember = cookies.cookieStore.get(origin).isNotEmpty()
            } catch (_: Exception) {
                clearSession()
            }
        }
    }

    fun hasSavedSession(): Boolean = remember && cookies.cookieStore.get(origin).isNotEmpty()

    fun setRemember(enabled: Boolean) {
        remember = enabled
        if (enabled) persistCookies() else preferences.edit().remove("session").apply()
    }

    fun clearSession() {
        remember = false
        cookies.cookieStore.removeAll()
        preferences.edit().remove("session").apply()
    }

    private fun persistCookies() {
        if (!remember) return
        try {
            val content = JSONArray()
            cookies.cookieStore.get(origin).filter { !it.hasExpired() && it.secure }.forEach { cookie ->
                content.put(JSONObject().put("name", cookie.name).put("value", cookie.value))
            }
            preferences.edit().putString("session", vault.encrypt(content.toString())).apply()
        } catch (_: Exception) {
            preferences.edit().remove("session").apply()
        }
    }

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        var last: IOException? = null
        repeat(2) { attempt ->
            try {
                return@withContext perform(path, null)
            } catch (e: IOException) {
                last = e
                if (attempt == 0) delay(500)
            }
        }
        throw ApiProblem(
            if (last is java.net.SocketTimeoutException) "الاتصال بطيء الآن. حاول مرة أخرى."
            else "تعذر الاتصال بالخادم عبر هذه الشبكة. تحقّق من الإنترنت."
        )
    }

    suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        try {
            perform(path, body)
        } catch (e: IOException) {
            // Never auto-replay a state-changing POST: the server might have committed it.
            throw ApiProblem(
                if (e is java.net.SocketTimeoutException)
                    "انتهت مهلة الاتصال. تحقق من حسابك قبل إعادة إرسال العملية."
                else "تعذر تأكيد نتيجة العملية. حدّث البيانات أولاً.",
                code = -1
            )
        }
    }

    private fun perform(path: String, body: JSONObject?): JSONObject {
        val safePath = path.trimStart('/')
        require(!safePath.startsWith("http", ignoreCase = true) &&
                !safePath.startsWith("//") && !safePath.contains("..") &&
                safePath.matches(Regex("[a-zA-Z0-9_/?.=&%\\-]+"))) { "مسار غير آمن" }
        val connection = URL(origin.toString() + safePath).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("X-Murshid-Client", "AndroidNative")
            cookies.cookieStore.get(origin).filter { !it.hasExpired() }.takeIf { it.isNotEmpty() }?.let { jar ->
                connection.setRequestProperty("Cookie", jar.joinToString("; ") { "${it.name}=${it.value}" })
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            cookies.put(origin, connection.headerFields)
            persistCookies()
            if (code in 300..399) throw ApiProblem("أعاد الخادم توجيه الطلب. تحقق من اتصالك.", code)
            val stream = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?: throw ApiProblem("استجابة الخادم فارغة.", code)
            val output = ByteArrayOutputStream()
            stream.use { input ->
                val buf = ByteArray(8192)
                var count = 0
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    count += n
                    if (count > 2_000_000) throw ApiProblem("الاستجابة كبيرة جداً.", code)
                    output.write(buf, 0, n)
                }
            }
            val json = try { JSONObject(output.toString("UTF-8")) } catch (_: Exception) {
                throw ApiProblem(if (code >= 500) "خطأ مؤقت في خادم المنصة."
                    else "تلقينا استجابة غير متوقعة من الخادم.", code)
            }
            if (code == 401 && safePath != "api/login.php") {
                clearSession()
                throw ApiProblem("انتهت الجلسة. سجّل الدخول مجدداً.", code, sessionExpired = true)
            }
            if (code !in 200..299 || !json.optBoolean("ok", true)) {
                throw ApiProblem(
                    json.optString("message").takeIf { it.isNotBlank() } ?: "تعذر إكمال العملية.",
                    code,
                    code == 402 || json.optBoolean("payment_required")
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    /** Multipart avatar upload, using the same form contract as the original iOS app. */
    suspend fun uploadAvatar(image: android.net.Uri, csrf: String): JSONObject =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(image)?.lowercase()
                ?: throw ApiProblem("تعذر معرفة نوع الصورة.")
            if (mime != "image/jpeg" && mime != "image/png") {
                throw ApiProblem("اختر صورة JPEG أو PNG.")
            }
            val bytes = context.contentResolver.openInputStream(image)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    count += n
                    if (count > 3_000_000) throw ApiProblem("حجم الصورة أكبر من 3 ميغابايت.")
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: throw ApiProblem("تعذر قراءة الصورة.")
            val boundary = "murshid_android_upload_boundary"
            val output = ByteArrayOutputStream()
            fun addText(text: String) = output.write(text.toByteArray(StandardCharsets.UTF_8))
            addText("--$boundary\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n$csrf\r\n")
            addText("--$boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"profile.${if (mime == "image/png") "png" else "jpg"}\"\r\n")
            addText("Content-Type: $mime\r\n\r\n")
            output.write(bytes)
            addText("\r\n--$boundary--\r\n")
            val url = URL(origin.toString() + "mobile/avatar.php")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.instanceFollowRedirects = false
                conn.doOutput = true
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                cookies.cookieStore.get(origin).filter { !it.hasExpired() }.let { jar ->
                    if (jar.isNotEmpty()) conn.setRequestProperty("Cookie", jar.joinToString("; ") {
                        "${it.name}=${it.value}"
                    })
                }
                val payload = output.toByteArray()
                conn.setFixedLengthStreamingMode(payload.size)
                conn.outputStream.use { it.write(payload) }
                parseUploadResponse(conn)
            } finally { conn.disconnect() }
        }

    suspend fun removeAvatar(csrf: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = "csrf=" + java.net.URLEncoder.encode(csrf, "UTF-8") + "&remove=1"
        val conn = URL(origin.toString() + "mobile/avatar.php").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            cookies.cookieStore.get(origin).filter { !it.hasExpired() }.let { jar ->
                if (jar.isNotEmpty()) conn.setRequestProperty("Cookie", jar.joinToString("; ") {
                    "${it.name}=${it.value}"
                })
            }
            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            parseUploadResponse(conn)
        } finally { conn.disconnect() }
    }

    private fun parseUploadResponse(conn: HttpURLConnection): JSONObject {
        val status = conn.responseCode
        cookies.put(origin, conn.headerFields)
        persistCookies()
        val content = (if (status >= 400) conn.errorStream else conn.inputStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(20_000) }.orEmpty()
        val response = try { JSONObject(content) } catch (_: Exception) {
            throw ApiProblem("تعذر قراءة استجابة رفع الصورة.", status)
        }
        if (status == 401) {
            clearSession()
            throw ApiProblem("انتهت جلسة الطالب.", status, sessionExpired = true)
        }
        if (status !in 200..299 || !response.optBoolean("ok", true)) {
            throw ApiProblem(response.optString("message", "تعذر تحديث الصورة."), status)
        }
        return response
    }

    private class SessionVault {
        private val alias = "murshid_android_from_ios_cookie_v1"

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build()
            )
            return generator.generateKey()
        }

        fun encrypt(text: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
            val buffer = byteArrayOf(iv.size.toByte()) + iv + encrypted
            return Base64.encodeToString(buffer, Base64.NO_WRAP)
        }

        fun decrypt(encoded: String): String {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.isEmpty()) throw IllegalArgumentException("Empty encrypted session")
            val ivLength = bytes[0].toInt() and 255
            if (ivLength !in 12..16 || bytes.size <= ivLength + 17) throw IllegalArgumentException("Invalid session")
            val iv = bytes.copyOfRange(1, 1 + ivLength)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)), StandardCharsets.UTF_8)
        }
    }
}
