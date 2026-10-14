package com.murshidwizari.student

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, account-bound and expiry-bound offline exam questions and pending answers.
 * No offline XP, rankings, subscription grants or official grades are generated.
 * Only authenticated HTTPS responses from mobile/bootstrap.php grant a cache window.
 * Replay uses the existing server grade-once endpoint after checking prior submissions.
 */
internal class OfflineExamBank(context: Context) {
    data class Topic(val id: Int, val name: String, val count: Int)
    data class SyncResult(val confirmed: Int, val remaining: Int)

    private val prefs = context.getSharedPreferences("murshid_encrypted_offline_exams_v1", Context.MODE_PRIVATE)
    private val alias = "murshid_offline_exam_aes_gcm_v1"
    private val day = 86_400_000L
    private val maxAge = 7 * day
    private val maxRecords = 300

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun encode(value: JSONObject): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val encrypted = cipher.doFinal(value.toString().toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decode(raw: String): JSONObject? = runCatching {
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        require(bytes.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8))
    }.getOrNull()

    private fun read(name: String): JSONObject? = prefs.getString(name, null)?.let(::decode)
    private fun write(name: String, item: JSONObject): Boolean = runCatching {
        // Persist synchronously: an app termination must not lose a pending answer.
        prefs.edit().putString(name, encode(item)).commit()
    }.getOrDefault(false)

    fun clear() { prefs.edit().clear().commit() }

    /** The exact subscription expiration is required; rounded remaining days are unsafe. */
    fun acceptVerifiedSubscription(userId: Int, subscribed: Boolean, expiryUnix: Long) {
        if (userId <= 0) return
        val old = read("access")?.optInt("user_id", 0) ?: 0
        if (old != 0 && old != userId) clear()
        val now = System.currentTimeMillis()
        if (!subscribed || expiryUnix <= now / 1000L) {
            // Do not discard unanswered drafts: expiry can be followed by renewal.
            prefs.edit().remove("access").commit()
        } else write("access", JSONObject().put("user_id", userId)
            .put("verified_at", now).put("expires_at", expiryUnix * 1000L))
    }

    fun canOpen(userId: Int, now: Long = System.currentTimeMillis()): Boolean {
        val a = read("access") ?: return false
        val verified = a.optLong("verified_at", 0L)
        val expires = a.optLong("expires_at", 0L)
        return a.optInt("user_id") == userId && userId > 0 && verified > 0 &&
            now + 300_000L >= verified && now < expires && now - verified < maxAge
    }

    private fun topicsKey(uid: Int) = "topics_\$uid"
    private fun topicKey(uid: Int, chapter: Int) = "topic_\$uid\_\$chapter"
    private fun pendingKey(uid: Int) = "pending_\$uid"

    fun saveTopic(uid: Int, chapterId: Int, chapterName: String, questions: JSONArray): Boolean {
        if (!canOpen(uid) || chapterId <= 0 || questions.length() < 1 ||
            questions.length() > 250 || chapterName.isBlank()) return false
        val raw = questions.toString()
        if (raw.length > 1_600_000) return false
        val filtered = JSONArray()
        for (i in 0 until questions.length()) {
            val q = questions.optJSONObject(i) ?: continue
            if (OfflineExamRules.supported(q.optString("type"), q.optJSONArray("options")?.length() ?: 0) &&
                q.optInt("id") > 0 && q.optString("text").isNotBlank()) filtered.put(q)
        }
        if (filtered.length() == 0) return false
        val stored = JSONObject().put("id", chapterId).put("name", chapterName.take(180))
            .put("saved_at", System.currentTimeMillis()).put("questions", filtered)
        if (!write(topicKey(uid, chapterId), stored)) return false
        val index = read(topicsKey(uid))?.optJSONArray("items") ?: JSONArray()
        val replaced = JSONArray()
        for (i in 0 until index.length()) {
            val old = index.optJSONObject(i) ?: continue
            if (old.optInt("id") != chapterId) replaced.put(old)
        }
        replaced.put(JSONObject().put("id", chapterId).put("name", chapterName.take(180))
            .put("count", filtered.length()).put("saved_at", System.currentTimeMillis()))
        // Keep a bounded bank; preserve pending answers separately.
        val recent = (0 until replaced.length()).mapNotNull { replaced.optJSONObject(it) }
            .sortedByDescending { it.optLong("saved_at", 0L) }.take(12)
        return write(topicsKey(uid), JSONObject().put("items", JSONArray(recent)))
    }

    fun topics(uid: Int): List<Topic> {
        if (!canOpen(uid)) return emptyList()
        val a = read(topicsKey(uid))?.optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            Topic(it.optInt("id"), it.optString("name"), it.optInt("count"))
        }.filter { it.id > 0 && it.count > 0 }
    }

    fun questions(uid: Int, chapterId: Int): JSONArray? {
        if (!canOpen(uid)) return null
        val saved = read(topicKey(uid, chapterId)) ?: return null
        if (saved.optInt("id") != chapterId ||
            System.currentTimeMillis() - saved.optLong("saved_at", 0L) !in 0L..maxAge) return null
        return saved.optJSONArray("questions")
    }

    private fun pending(uid: Int): JSONArray =
        read(pendingKey(uid))?.optJSONArray("items") ?: JSONArray()

    fun pendingCount(uid: Int): Int = if (uid <= 0) 0 else pending(uid).length()

    fun queued(uid: Int, questionId: Int): JSONObject? {
        val list = pending(uid)
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i)
            if (row?.optInt("question_id") == questionId) return row
        }
        return null
    }

    fun queue(uid: Int, chapterId: Int, questionId: Int, questionIndex: Int,
              text: String, optionId: Int): Boolean {
        if (!canOpen(uid) || questionId <= 0 || questionIndex <= 0 || text.isBlank() ||
            text.length > 3000) return false
        val savedQuestions = questions(uid, chapterId) ?: return false
        val known = (0 until savedQuestions.length()).mapNotNull { savedQuestions.optJSONObject(it) }
            .firstOrNull { it.optInt("id") == questionId } ?: return false
        if (known.optBoolean("submitted", false)) return false
        val list = pending(uid)
        if (queued(uid, questionId) != null) return true
        if (list.length() >= maxRecords) return false
        val row = JSONObject().put("chapter_id", chapterId).put("question_id", questionId)
            .put("question_index", questionIndex).put("answer", text.take(3000))
            .put("option_id", optionId).put("created_at", System.currentTimeMillis())
        list.put(row)
        return write(pendingKey(uid), JSONObject().put("items", list))
    }

    /** Call from a serial background executor only. Every ambiguous answer is
     * reconciled with mobile/exam.php before a possible resend.
     */
    fun sync(api: ApiClient, csrf: String, uid: Int): SyncResult {
        if (uid <= 0 || csrf.isBlank()) return SyncResult(0, pendingCount(uid))
        val list = pending(uid)
        var confirmed = 0
        var working = JSONArray()
        var stopped = false
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i) ?: continue
            if (stopped) { working.put(row); continue }
            val chapter = row.optInt("chapter_id")
            val qid = row.optInt("question_id")
            try {
                val snapshot = api.call("mobile/exam.php?chapter_id=\$chapter")
                if (!snapshot.optBoolean("ok")) { working.put(row); stopped = true; continue }
                val live = snapshot.optJSONArray("questions") ?: JSONArray()
                val found = (0 until live.length()).mapNotNull { live.optJSONObject(it) }
                    .firstOrNull { it.optInt("id") == qid }
                if (found == null) { working.put(row); stopped = true; continue }
                if (found.optBoolean("submitted", false)) {
                    confirmed++
                    // Flush the confirmed prefix immediately, even if later requests fail.
                } else {
                    val answer = row.optString("answer")
                    val value = JSONObject().put("text", answer).put("selected_text", answer)
                        .put("submitted", true)
                    if (row.optInt("option_id") > 0) value.put("selected", row.optInt("option_id"))
                    val payload = JSONObject().put("csrf", csrf).put("chapter_id", chapter)
                        .put("question_index", row.optInt("question_index"))
                        .put("completed", false)
                        .put("answers", JSONObject().put(qid.toString(), value))
                    val response = api.call("api/progress.php", payload)
                    if (response.optBoolean("ok") && response.optJSONObject("grades")?.has(qid.toString()) == true) {
                        confirmed++
                    } else { working.put(row); stopped = true }
                }
            } catch (_: Exception) { working.put(row); stopped = true }
            // Persist every processed element together with the untouched suffix.
            val suffix = JSONArray()
            for (j in i + 1 until list.length()) list.optJSONObject(j)?.let { suffix.put(it) }
            val checkpoint = JSONArray()
            for (j in 0 until working.length()) checkpoint.put(working.optJSONObject(j))
            for (j in 0 until suffix.length()) checkpoint.put(suffix.optJSONObject(j))
            write(pendingKey(uid), JSONObject().put("items", checkpoint))
        }
        return SyncResult(confirmed, pendingCount(uid))
    }
}

/** Multi-stage math/genetics/matching require a specialized editor; never silently
 * send plain text and misgrade them as zero during offline practice.
 */
internal object OfflineExamRules {
    fun supported(type: String, optionsCount: Int): Boolean = when (type) {
        "mcq", "true_false", "text", "fill", "meaning" -> true
        else -> false
    }
}
