package com.murshidwizari.student

/** iOS-compatible input rules for sending questions to the content team's inbox. */
internal object ProposalRules {
    val types = setOf("text", "mcq", "true_false", "fill", "meaning")

    fun error(chapterId: Int, type: String, question: String, correct: String,
              options: List<String>): String? = when {
        chapterId <= 0 -> "اختر الموضوع أولاً."
        type !in types -> "نوع السؤال غير صالح."
        question.trim().length < 6 -> "اكتب نص السؤال من ستة أحرف على الأقل."
        correct.trim().isEmpty() -> "أدخل الإجابة الصحيحة."
        type == "true_false" && correct.trim() !in setOf("صح", "خطأ") -> "اختر صح أو خطأ."
        type == "mcq" && options.map(String::trim).filter(String::isNotEmpty).distinct().size < 2 ->
            "اكتب خيارين مختلفين على الأقل."
        type == "mcq" && correct.trim() !in options.map(String::trim) ->
            "يجب أن تطابق الإجابة الصحيحة أحد الخيارات."
        else -> null
    }
}
