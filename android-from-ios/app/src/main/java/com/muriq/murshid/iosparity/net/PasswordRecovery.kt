package com.muriq.murshid.iosparity.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

data class RecoveryStart(val phoneToken: String?, val message: String)

/** Native implementation of the official iOS password-reset form protocol. */
class PasswordRecovery {
    private val origin = URI.create("https://www.mur-iq.com/")
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val tokenRegex = Regex("[a-f0-9]{64}")
    private data class Reply(val html: String, val status: Int, val redirect: String?)

    private fun request(path: String, form: Map<String, String>? = null): Reply {
        require(path.startsWith("forgot-password.php") || path.startsWith("reset-password.php"))
        val conn = origin.resolve(path).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val saved = cookies.cookieStore.get(origin).filter { !it.hasExpired() }
            if (saved.isNotEmpty()) conn.setRequestProperty("Cookie", saved.joinToString("; ") { it.name + "=" + it.value })
            if (form != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                val encoded = form.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
                conn.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            cookies.put(origin, conn.headerFields)
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val html = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(500000) }.orEmpty()
            if (status >= 400) throw ApiProblem(parseMessage(html).first.ifBlank { "تعذر تنفيذ الاستعادة ($status)." }, status)
            return Reply(html, status, conn.getHeaderField("Location"))
        } finally { conn.disconnect() }
    }

    private fun csrf(path: String): String {
        val html = request(path).html
        val input = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
            .map { it.value }.firstOrNull { Regex("name\\s*=\\s*[\\\"\u0027]csrf[\\\"\u0027]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: throw ApiProblem("تعذر قراءة حماية الاستعادة.")
        return Regex("value\\s*=\\s*[\\\"\u0027]([^\\\"\u0027]+)[\\\"\u0027]", RegexOption.IGNORE_CASE)
            .find(input)?.groupValues?.get(1) ?: throw ApiProblem("تعذر قراءة رمز الحماية.")
    }

    private fun parseMessage(html: String): Pair<String, Boolean> {
        val found = Regex("<div[^>]*class=[\\\"\u0027][^\\\"\u0027]*form-message\\s+([^\\\"\u0027]+)[\\\"\u0027][^>]*>(.*?)</div>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html) ?: return "" to false
        val clean = found.groupValues[2].replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&nbsp;", " ").trim()
        return clean to found.groupValues[1].contains("error", true)
    }

    suspend fun begin(identifier: String): RecoveryStart = withContext(Dispatchers.IO) {
        require(identifier.isNotBlank()) { "أدخل البريد أو رقم الهاتف." }
        val response = request("forgot-password.php", mapOf("csrf" to csrf("forgot-password.php"), "identifier" to identifier.trim()))
        val destination = response.redirect?.let { origin.resolve(it) }
        val token = destination?.takeIf { it.host == origin.host && it.path == "/reset-password.php" }
            ?.rawQuery?.split("&")?.firstOrNull { it.startsWith("phone_token=") }?.substringAfter("=")
        if (token != null && tokenRegex.matches(token))
            return@withContext RecoveryStart(token, "أُرسل رمز الاستعادة إلى WhatsApp المسجل.")
        if (response.status in 300..399) throw ApiProblem("إعادة توجيه غير متوقعة من الخادم.")
        val (message, error) = parseMessage(response.html)
        if (error) throw ApiProblem(message.ifBlank { "تعذر طلب الاستعادة." })
        RecoveryStart(null, message.ifBlank { "إذا كان البريد مسجلاً ستصل رسالة الاستعادة إليه." })
    }

    suspend fun resend(token: String): String = withContext(Dispatchers.IO) {
        require(tokenRegex.matches(token)) { "رمز الجلسة غير صالح." }
        val response = request("reset-password.php", mapOf("csrf" to csrf("reset-password.php?phone_token=$token"), "phone_token" to token, "action" to "resend"))
        val (message, error) = parseMessage(response.html)
        if (error || response.status !in 200..299) throw ApiProblem(message.ifBlank { "تعذر إعادة إرسال الرمز." })
        message.ifBlank { "تم طلب رمز جديد." }
    }

    suspend fun finish(token: String, code: String, password: String, confirm: String): String = withContext(Dispatchers.IO) {
        require(tokenRegex.matches(token)) { "رمز الجلسة غير صالح." }
        require(code.matches(Regex("[0-9]{6}"))) { "أدخل رمزاً من 6 أرقام." }
        require(password.length >= 8 && password == confirm) { "كلمة المرور لا تطابق التأكيد أو قصيرة." }
        val response = request("reset-password.php", mapOf("csrf" to csrf("reset-password.php?phone_token=$token"),
            "phone_token" to token, "action" to "reset", "code" to code, "password" to password, "confirm" to confirm))
        val (message, error) = parseMessage(response.html)
        if (error || !message.contains("تم تغيير كلمة المرور")) throw ApiProblem(message.ifBlank { "تعذر تغيير كلمة المرور." })
        cookies.cookieStore.removeAll()
        message
    }
}
