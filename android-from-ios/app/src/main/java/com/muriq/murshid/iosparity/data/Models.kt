package com.muriq.murshid.iosparity.data

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.array(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()
fun JSONObject.text(key: String, default: String = ""): String =
    optString(key, default).takeUnless { it == "null" } ?: default
fun JSONObject.num(key: String, default: Int = 0): Int =
    if (isNull(key)) default else optInt(key, default)

data class Student(
    val id: Int, val name: String, val grade: String, val avatar: String
) {
    companion object {
        fun from(json: JSONObject) = Student(
            json.num("id"), json.text("name", "الطالب"),
            json.text("grade", json.text("grade_name")), json.text("avatar_url")
        )
    }
}

data class Subject(val id: Int, val name: String, val count: Int) {
    companion object {
        fun from(json: JSONObject) = Subject(
            json.num("id"), json.text("name"), json.num("questions_count", json.num("count"))
        )
        fun visible(list: List<Subject>): List<Subject> {
            val kept = list.filterNot {
                val name = it.name.lowercase()
                    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                name.contains("فرنس") || name.contains("french") || name.contains("franc")
            }
            val arabic = kept.indexOfFirst { it.name.contains("اللغة العربية") }
            val literature = kept.indexOfFirst { it.name.contains("الأدب") }
            if (arabic >= 0 && literature >= 0 && literature != arabic + 1) {
                val copy = kept.toMutableList()
                val literatureSubject = copy.removeAt(literature)
                copy.add(copy.indexOfFirst { it.name.contains("اللغة العربية") } + 1, literatureSubject)
                return copy
            }
            return kept
        }
    }
}

data class Topic(val id: Int, val name: String, val count: Int) {
    companion object {
        fun from(json: JSONObject) = Topic(
            json.num("id"), json.text("name"),
            json.num("count", json.num("question_count", json.num("questions_count")))
        )
    }
}

data class AnswerOption(val id: String, val title: String) {
    companion object {
        fun fromAny(raw: Any?, position: Int): AnswerOption {
            if (raw is JSONObject) return AnswerOption(
                raw.optString("id", (position + 1).toString()),
                raw.text("text", raw.text("label", raw.text("value")))
            )
            return AnswerOption((position + 1).toString(), raw?.toString().orEmpty())
        }
    }
}

data class QuizQuestion(
    val id: Int,
    val text: String,
    val type: String,
    val answerMode: String,
    val correctAnswer: String,
    val explanation: String,
    val options: List<AnswerOption>,
    val submitted: Boolean,
    val savedAnswer: JSONObject?,
    val raw: JSONObject
) {
    companion object {
        fun from(json: JSONObject): QuizQuestion {
            val optionsArray = json.optJSONArray("options") ?: JSONArray()
            return QuizQuestion(
                id = json.num("id"),
                text = json.text("text"),
                type = json.text("type"),
                answerMode = json.text("answer_mode"),
                correctAnswer = json.text("correct_answer"),
                explanation = json.text("explanation"),
                options = (0 until optionsArray.length()).map {
                    AnswerOption.fromAny(optionsArray.opt(it), it)
                },
                submitted = json.optBoolean("submitted"),
                savedAnswer = json.optJSONObject("answer"),
                raw = json
            )
        }
    }
}

data class SubscriptionPlan(val id: String, val label: String, val amount: Int) {
    companion object {
        fun from(json: JSONObject) = SubscriptionPlan(
            json.text("key", json.text("id", "month1")), json.text("label", json.text("name")),
            json.num("amount", json.num("price"))
        )
    }
}

data class StudentDashboard(
    val user: Student,
    val csrf: String,
    val subjects: List<Subject>,
    val plans: List<SubscriptionPlan>,
    val stats: JSONObject,
    val ads: List<JSONObject>,
    val subscribed: Boolean,
    val remainingDays: Int?,
    val freeRemaining: Int,
    val freeLimit: Int,
    val freeUsed: Int
) {
    companion object {
        fun from(json: JSONObject) = StudentDashboard(
            user = Student.from(json.optJSONObject("user") ?: JSONObject()),
            csrf = json.text("csrf"),
            subjects = Subject.visible(json.array("subjects").map(Subject::from)),
            plans = json.array("plans").map(SubscriptionPlan::from),
            stats = json.optJSONObject("stats") ?: JSONObject(),
            ads = json.array("ads"),
            subscribed = json.optBoolean("subscribed"),
            remainingDays = if (json.isNull("subscription_days_remaining")) null
                else json.num("subscription_days_remaining"),
            freeRemaining = json.num("free_remaining"),
            freeLimit = json.num("free_limit", 15),
            freeUsed = json.num("free_used")
        )
    }
}
