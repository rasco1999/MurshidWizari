package com.murshidwizari.student

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    data class SyncResult(val confirmed: Int, val remaining: Int, val changedQuestions: Int = 0)

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

    private fun topicsKey(uid: Int) = "topics_" + uid
    private fun topicKey(uid: Int, chapter: Int) = "topic_" + uid + "_" + chapter
    private fun pendingKey(uid: Int) = "pending_" + uid

    @Synchronized
    fun saveTopic(uid: Int, chapterId: Int, chapterName: String, questions: JSONArray): Boolean {
        if (!canOpen(uid) || chapterId <= 0 || questions.length() < 1 ||
            questions.length() > 250 || chapterName.isBlank()) return false
        val drafts = pending(uid)
        val pinned = (0 until drafts.length()).mapNotNull { drafts.optJSONObject(it) }
            .map { it.optInt("chapter_id") }.toSet()
        // Keep the exact question text that the student answered until every
        // draft in this topic is confirmed or explicitly discarded.
        if (chapterId in pinned || pinned.size >= 12) return false
        val raw = questions.toString()
        if (raw.length > 1_600_000) return false
        val filtered = JSONArray()
        for (i in 0 until questions.length()) {
            val q = questions.optJSONObject(i) ?: continue
            if (OfflineExamRules.supported(q.optString("type"), q.optJSONArray("options")?.length() ?: 0) &&
                q.optInt("id") > 0 && q.optString("text").isNotBlank()) {
                val item = JSONObject(q.toString())
                item.put("_offline_index", i + 1) // Preserve original server question order.
                filtered.put(item)
            }
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
        val ordered = (0 until replaced.length()).mapNotNull { replaced.optJSONObject(it) }
            .sortedByDescending { it.optLong("saved_at", 0L) }
        val pinnedTopics = ordered.filter { it.optInt("id") in pinned }
        val recent = pinnedTopics + ordered.filter { it.optInt("id") !in pinned }
            .take((12 - pinnedTopics.size).coerceAtLeast(0))
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

    /**
     * A changed question must not be graded using an answer written for an older revision.
     * Uses exact source content and option IDs, not cosmetic metadata such as image URL or year.
     */
    private fun questionSignature(item: JSONObject): String {
        val options = item.optJSONArray("options") ?: JSONArray()
        val choices = (0 until options.length()).map { index ->
            val obj = options.optJSONObject(index)
            if (obj != null) obj.optInt("id") to obj.optString("text")
            else 0 to options.optString(index)
        }
        return OfflineExamRules.signature(
            item.optInt("id"), item.optString("type"), item.optString("text"),
            item.optString("meaning_word"), item.optString("correct_answer"), choices
        )
    }

    fun pendingCount(uid: Int): Int = if (uid <= 0) 0 else pending(uid).length()

    fun queued(uid: Int, questionId: Int): JSONObject? {
        val list = pending(uid)
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i)
            if (row?.optInt("question_id") == questionId) return row
        }
        return null
    }

    @Synchronized
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
            .put("question_signature", questionSignature(known))
            .put("option_id", optionId).put("created_at", System.currentTimeMillis())
        list.put(row)
        return write(pendingKey(uid), JSONObject().put("items", list))
    }

    /** Explicit student action: dismiss an unsent draft after a server-side revision.
     * Never silently lose pending answers while syncing.
     */
    @Synchronized
    fun discardPending(uid: Int, questionId: Int): Boolean {
        if (uid <= 0 || questionId <= 0) return false
        return acknowledge(uid, questionId, accepted = false)
    }

    @Synchronized
    private fun acknowledge(uid: Int, questionId: Int, accepted: Boolean = true): Boolean {
        val latest = pending(uid)
        val kept = JSONArray()
        for (i in 0 until latest.length()) {
            val entry = latest.optJSONObject(i) ?: continue
            if (entry.optInt("question_id") != questionId) kept.put(entry)
        }
        // Update the cached receipt and remove the queue entry in one disk commit.
        // A confirmed answer must remain answered after an offline process restart.
        return runCatching {
            val editor = prefs.edit().putString(pendingKey(uid), encode(JSONObject().put("items", kept)))
            if (accepted) {
                val entry = (0 until latest.length()).mapNotNull { latest.optJSONObject(it) }
                    .firstOrNull { it.optInt("question_id") == questionId }
                val chapter = entry?.optInt("chapter_id") ?: 0
                val cached = read(topicKey(uid, chapter))
                val items = cached?.optJSONArray("questions")
                for (i in 0 until (items?.length() ?: 0)) {
                    val item = items?.optJSONObject(i) ?: continue
                    if (item.optInt("id") == questionId) item.put("submitted", true)
                }
                if (cached != null) editor.putString(topicKey(uid, chapter), encode(cached))
            }
            editor.commit()
        }.getOrDefault(false)
    }

    /** Synchronize on a serial background executor. Reconcile each ambiguous
     * question with the server BEFORE resending; preserve failures and all
     * locally added entries atomically across app restarts.
     */
    fun sync(api: ApiClient, csrf: String, uid: Int): SyncResult {
        if (uid <= 0 || csrf.isBlank()) return SyncResult(0, pendingCount(uid))
        val snapshot = pending(uid)
        var confirmed = 0
        var changed = 0
        for (i in 0 until snapshot.length()) {
            val row = snapshot.optJSONObject(i) ?: continue
            val chapter = row.optInt("chapter_id")
            val qid = row.optInt("question_id")
            try {
                val serverExam = api.call("mobile/exam.php?chapter_id=" + chapter)
                if (!serverExam.optBoolean("ok")) break
                val live = serverExam.optJSONArray("questions") ?: JSONArray()
                val matchedIndex = (0 until live.length()).firstOrNull {
                    live.optJSONObject(it)?.optInt("id") == qid
                }
                if (matchedIndex == null) {
                    changed++
                    continue
                }
                val found = live.optJSONObject(matchedIndex) ?: break
                if (found.optBoolean("submitted", false)) {
                    if (!acknowledge(uid, qid)) break
                    confirmed++
                    continue
                }
                val downloaded = questions(uid, chapter)
                val original = (0 until (downloaded?.length() ?: 0))
                    .mapNotNull { downloaded?.optJSONObject(it) }
                    .firstOrNull { it.optInt("id") == qid }
                val expected = row.optString("question_signature")
                    .ifBlank { original?.let(::questionSignature).orEmpty() }
                // If no snapshot survives, do not optimistically replay unknown content.
                if (expected.isBlank() || expected != questionSignature(found)) {
                    changed++
                    continue
                }
                val answer = row.optString("answer")
                val value = JSONObject().put("text", answer).put("selected_text", answer)
                    .put("submitted", true)
                if (row.optInt("option_id") > 0) value.put("selected", row.optInt("option_id"))
                val payload = JSONObject().put("csrf", csrf).put("chapter_id", chapter)
                    .put("question_index", matchedIndex + 1)
                    .put("completed", false)
                    .put("answers", JSONObject().put(qid.toString(), value))
                val response = api.call("api/progress.php", payload)
                if (!response.optBoolean("ok") ||
                    response.optJSONObject("grades")?.has(qid.toString()) != true) break
                if (!acknowledge(uid, qid)) break
                confirmed++
            } catch (_: Exception) { break }
        }
        return SyncResult(confirmed, pendingCount(uid), changed)
    }
}

/** Multi-stage math/genetics/matching require a specialized editor; never silently
 * send plain text and misgrade them as zero during offline practice.
 */
internal object OfflineExamRules {
    fun signature(id: Int, type: String, question: String, meaning: String,
                  answer: String, options: List<Pair<Int, String>>): String {
        val fields = mutableListOf(id.toString(), type, question, meaning, answer,
            options.size.toString())
        options.forEach { (optionId, label) ->
            fields.add(optionId.toString())
            fields.add(label)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(
            fields.joinToString(separator = "\u001f").toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun supported(type: String, optionsCount: Int): Boolean = when (type) {
        "mcq" -> optionsCount >= 2
        "true_false", "text", "fill", "meaning" -> true
        else -> false
    }
}
