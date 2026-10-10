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
