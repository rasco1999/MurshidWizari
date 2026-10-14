package com.murshidwizari.student

/**
 * Private study coaching heuristics. These are NOT official exam marks and
 * NEVER change the server's grades, paid entitlements, rankings or quota.
 */
internal object LearningInsights {
    data class Review(
        val subject: String,
        val successfulReviews: Int,
        val wasCorrect: Boolean?,
        val dueAt: Long,
        val lastReviewedAt: Long
    )

    data class Mastery(
        val subject: String,
        val total: Int,
        val due: Int,
        val secure: Int,
        val needsPractice: Int
    ) {
        val percent: Int get() = if (total == 0) 0 else (100 * secure / total)
    }

    fun mastery(reviews: List<Review>, now: Long): List<Mastery> =
        reviews.filter { it.subject.isNotBlank() }
            .groupBy { it.subject.trim().substringBefore(" · ").trim() }
            .map { (subject, questions) ->
                val secure = questions.count { it.successfulReviews >= 3 }
                Mastery(subject, questions.size,
                    due = questions.count { it.dueAt <= now },
                    secure = secure,
                    needsPractice = questions.count { it.wasCorrect == false || it.successfulReviews < 2 })
            }
            .sortedWith(compareBy<Mastery> { it.percent }.thenByDescending { it.due }
                .thenBy { it.subject })

    /** 10–30 questions/day, limited to sustainable short study sessions. */
    fun suggestedDailyTarget(reviews: List<Review>, now: Long): Int {
        if (reviews.isEmpty()) return 10
        val due = reviews.count { it.dueAt <= now }
        val weak = reviews.count { it.wasCorrect == false || it.successfulReviews < 2 }
        val goal = (due + minOf(weak / 3, 10)).coerceIn(10, 30)
        return (goal + 4) / 5 * 5
    }

    fun nextReview(reviews: List<Review>, now: Long): String {
        if (reviews.isEmpty()) return "ابدأ بالإجابة عن أسئلة مصححة لبناء خطة مراجعة شخصية."
        val due = reviews.count { it.dueAt <= now }
        if (due > 0) return "ابدأ بـ $due سؤال مستحق، ثم انتقل إلى المواضيع الأضعف."
        return "لا توجد مراجعات مستحقة الآن. خصص وقتاً لمسألة جديدة أو موضوع صعب."
    }
}
