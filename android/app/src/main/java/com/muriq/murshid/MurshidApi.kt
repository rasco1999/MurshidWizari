package com.muriq.murshid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

const val SERVER = "https://www.mur-iq.com"
typealias Json = JSONObject

fun Json.str(key: String): String = optString(key, "").takeUnless { it == "null" } ?: ""
fun Json.obj(key: String): Json = optJSONObject(key) ?: JSONObject()
fun Json.arr(key: String): List<Json> {
    val items = optJSONArray(key) ?: return emptyList()
    return (0 until items.length()).mapNotNull { items.optJSONObject(it) }
}
fun Json.int(key: String): Int = optInt(key, 0)
fun Json.bool(key: String): Boolean = optBoolean(key, false)
fun Json.asMap(): Map<String, Any?> = keys().asSequence().associateWith { opt(it) }
fun payload(vararg args: Pair<String, Any?>): Json = JSONObject().apply {
    args.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
}
fun JSONObject.readMessage(default: String) = str("message").ifBlank { default }

class ServerError(val code: Int, override val message: String) : RuntimeException(message) {
    val isSessionExpired get() = code == 401 || code == 403
    val paymentRequired get() = code == 402
}

class MurshidApi(private val context: Context) {
    private val cookies get() = CookieManager.getInstance()

    private fun url(path: String, query: Map<String, String>): URL {
        val base = Uri.parse(SERVER).buildUpon().appendEncodedPath(path)
        query.forEach { (k, v) -> base.appendQueryParameter(k, v) }
        return URL(base.build().toString())
    }

    private fun requestConnection(path: String, query: Map<String, String>, method: String): HttpURLConnection {
        return (url(path, query).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 22000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Murshid-App", "android-native-1.0")
            cookies.getCookie(SERVER)?.let { setRequestProperty("Cookie", it) }
        }
    }

    private fun saveCookies(connection: HttpURLConnection) {
        connection.headerFields.entries
            .filter { it.key?.equals("set-cookie", true) == true }
            .flatMap { it.value ?: emptyList() }
            .forEach { cookies.setCookie(SERVER, it) }
        cookies.flush()
    }

    suspend fun call(
        path: String,
        query: Map<String, String> = emptyMap(),
        data: Json? = null
    ): Json = withContext(Dispatchers.IO) {
        val connection = requestConnection(path, query, if (data == null) "GET" else "POST")
        try {
            if (data != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            saveCookies(connection)
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = try { JSONObject(text) } catch (_: Exception) {
                throw ServerError(code, if (code in 300..399) "تعذر متابعة الطلب؛ تحقق من رابط الموقع." else "استجابة غير صالحة من الخادم. ($code)")
            }
            if (code !in 200..299 || !response.optBoolean("ok", true)) {
                throw ServerError(code, response.readMessage("تعذر إكمال العملية ($code)."))
            }
            response
        } finally { connection.disconnect() }
    }

    suspend fun avatarImage(fullUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!fullUrl.startsWith(SERVER) && !fullUrl.startsWith("https://mur-iq.com/")) return@withContext null
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.useCaches = false
            cookies.getCookie(SERVER)?.let { connection.setRequestProperty("Cookie", it) }
            if (connection.responseCode != 200) return@withContext null
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (_: Exception) { null }
        finally { connection.disconnect() }
    }

    suspend fun uploadAvatar(image: ByteArray, csrf: String): Json = withContext(Dispatchers.IO) {
        val boundary = "MurshidAndroid-${UUID.randomUUID()}"
        val connection = requestConnection("mobile/avatar.php", emptyMap(), "POST")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                fun write(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                write("--$boundary\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n$csrf\r\n")
                write("--$boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"avatar.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                out.write(image)
                write("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            saveCookies(connection)
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { throw ServerError(code, "استجابة رفع الصورة غير صالحة.") }
            if (code !in 200..299 || !json.optBoolean("ok", false)) {
                throw ServerError(code, json.readMessage("تعذر تغيير صورة الحساب."))
            }
            json
        } finally { connection.disconnect() }
    }

    suspend fun preparedPhoto(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val image = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("الصورة غير صالحة.")
        val maxEdge = maxOf(image.width, image.height)
        val ratio = minOf(1f, 1200f / maxEdge)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(image, (image.width * ratio).toInt().coerceAtLeast(1),
                (image.height * ratio).toInt().coerceAtLeast(1), true)
        } else image
        val buffer = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, buffer)
        if (scaled !== image) scaled.recycle()
        val bytes = buffer.toByteArray()
        if (bytes.size > 5 * 1024 * 1024) throw IllegalArgumentException("الصورة كبيرة جدًا.")
        bytes
    }

    fun clearSession() {
        cookies.removeAllCookies(null)
        cookies.flush()
    }
}
