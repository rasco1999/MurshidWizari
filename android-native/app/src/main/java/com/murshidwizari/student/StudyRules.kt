package com.murshidwizari.student

/** Matches the iOS study-plan steppers and the school-ad support form. */
internal object StudyRules {
    fun planError(title: String, days: Int, target: Int): String? = when {
        title.trim().isEmpty() -> "أدخل اسم الخطة."
        title.trim().length > 120 -> "اسم الخطة طويل جداً."
        days !in 1..365 -> "اختر مدة بين يوم و365 يوماً."
        target !in 5..500 || target % 5 != 0 -> "الهدف اليومي من 5 إلى 500 سؤال، بفواصل 5."
        else -> null
    }

    fun schoolAdError(school: String, advertisement: String, days: Int): String? = when {
        school.trim().length < 3 -> "اكتب اسم المدرسة."
        advertisement.trim().length < 10 -> "اكتب نص الإعلان الكامل."
        days !in 1..90 -> "مدة الإعلان من يوم إلى 90 يوماً."
        else -> null
    }

    fun schoolAdMessage(school: String, advertisement: String, days: Int, student: String): String =
        "طلب إعلان مدرسة — تطبيق Android\n" +
            "اسم المدرسة: ${school.trim()}\n" +
            "الإعلان الكامل:\n${advertisement.trim()}\n" +
            "عدد الأيام: $days\n" +
            "الطالب المرسل: ${student.trim().ifBlank { "طالب" }}"
}
