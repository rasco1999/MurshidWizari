package com.murshidwizari.student

/** Only confirmed server grades contribute to accuracy or incorrect totals. */
internal object ExamResultRules {
    data class Summary(val answered: Int, val graded: Int, val correct: Int) {
        val wrong: Int get() = (graded - correct).coerceAtLeast(0)
        val pending: Int get() = (answered - graded).coerceAtLeast(0)
        val percent: Int get() = if (graded > 0)
            ((correct.coerceIn(0, graded) * 100.0 / graded) + 0.5).toInt() else 0
        val title: String get() = when {
            graded == 0 -> "بانتظار التصحيح"
            percent >= 85 -> "أداء ممتاز"
            percent >= 60 -> "تقدم جيد"
            else -> "راجع الفكرة وحاول مرة أخرى"
        }
        val advice: String get() = when {
            graded == 0 -> "لم تصل درجات مؤكدة من الخادم بعد. حدّث الاختبار للتحقق من تصحيح الإجابات."
            percent >= 85 -> "انتقل إلى الموضوع التالي، وارجع إلى أخطائك القليلة في المراجعة الذكية لاحقًا."
            percent >= 60 -> "راجع الإجابات غير الصحيحة ثم أكمل الموضوع التالي حتى تثبت الفكرة."
            else -> "الأفضل مراجعة الأخطاء الآن ثم العودة لنفس الموضوع بعد فترة قصيرة."
        }
    }
}
