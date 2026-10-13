package com.murshidwizari.student

/** Native equation guidance: never grades answers or changes server quotas. */
internal object EquationStudyRules {
    private val formula = Regex("[=√∑∫×÷≤≥±²³→⇌^]")
    private val mathVerbs = listOf("احسب", "أوجد", "اوجد", "جد قيمة", "قانون", "المعادلة",
        "معادلة", "الميل", "الإحداثيات", "الاحداثيات", "المساحة", "المحيط", "السرعة",
        "التسارع", "المقاومة", "الكتلة", "التركيز", "مول", "وراث", "احتمال", "تفاعل")
    private val numeric = Regex("[0-9٠-٩۰-۹]")
    private val quantitative = listOf("رياضيات", "فيزياء", "كيمياء", "أحياء", "اقتصاد",
        "جغرافية", "علوم", "الرياضيات", "الفيزياء", "الكيمياء", "الأحياء")
    val frenchSubjectIds = setOf(33, 36, 40) // Verified against SQL snapshot 2026-10-14

    fun isFrenchSubject(id: Int, name: String): Boolean {
        val n = name.trim().lowercase()
        return id in frenchSubjectIds || "فرنسي" in n || "french" in n ||
            "français" in n || "francais" in n
    }

    fun applies(question: String, subject: String, kind: String = ""): Boolean {
        val q = question.trim()
        if (kind == "calculation" || kind == "genetics") return true
        if (q.length < 6) return false
        if (formula.containsMatchIn(q)) return true
        return quantitative.any { subject.contains(it) } &&
            numeric.containsMatchIn(q) && mathVerbs.any { q.contains(it) }
    }

    fun coordinateQuestion(question: String, chapter: String = ""): Boolean =
        listOf("الإحداثيات", "الاحداثيات", "المستقيم", "التمثيل البياني", "الميل", "النقاط")
            .any { (question + " " + chapter).contains(it) }

    fun steps(question: String, subject: String, chapter: String = ""): List<String> {
        val context = question + " " + chapter
        if (coordinateQuestion(context)) return listOf(
            "حدّد المطلوب: ميل أو معادلة مستقيم أو تمثيل بياني.",
            "افصل المعطيات واكتب كل نقطة على صورة (س، ص) مع مراعاة الإشارات.",
            "اختر قانون الميل أو علاقة التوازي أو التعامد المناسبة.",
            "رتّب المعادلة وعوّض، ثم تحقّق من صحة الحل باستخدام المعطيات."
        )
        if ("كيمياء" in subject) return listOf(
            "حدّد المطلوب: تفاعل أو مولات أو تركيز أو اتزان.",
            "افصل المعطيات واكتب الرموز والوحدات بدقة.",
            "اكتب العلاقة المناسبة؛ ووازن التفاعل دون تغيير الأرقام داخل الصيغ.",
            "عوّض واحسب، ثم تحقّق من توازن الذرات وصحة الوحدات."
        )
        if ("فيزياء" in subject) return listOf(
            "حدّد الكمية الفيزيائية المجهولة.",
            "افصل المعطيات ثم حوّل الوحدات إلى نظام متوافق.",
            "اختر القانون الذي يربط المجهول بالمعطيات ورتّبه.",
            "عوّض ثم تحقّق من وحدة الناتج ومنطقيته."
        )
        if ("أحياء" in subject || "وراث" in context || "جين" in context) return listOf(
            "حدّد الصفات أو الرموز والمعطيات المطلوبة.",
            "اكتب أنماط الأليلات وميّز الصفة السائدة والمتنحية عند الحاجة.",
            "رتّب جدول التزاوج أو قانون الاحتمال دون افتراض قيم غير معطاة.",
            "احسب النسبة وتحقّق من مجموع الاحتمالات."
        )
        return listOf(
            "اقرأ السؤال وحدّد المطلوب.",
            "افصل المعطيات والرموز والإشارات والوحدات.",
            "اختر القانون الملائم للموضوع ثم رتّبه.",
            "عوّض واحسب، وتحقّق من الناتج والوحدة."
        )
    }
}
