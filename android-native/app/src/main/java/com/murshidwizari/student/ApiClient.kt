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
        if (safePath.startsWith("http:") || safePath.startsWith("https:") || safePath.startsWith("//"))
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
            if (status == 401 && safePath != "api/login.php") response.put("session_expired", true)
            return response
        } finally { conn.disconnect() }
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
