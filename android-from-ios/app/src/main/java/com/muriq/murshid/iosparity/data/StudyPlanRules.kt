package com.muriq.murshid.iosparity.data

object StudyPlanRules {
    fun error(title: String, days: Int, target: Int): String? {
        if (title.trim().length !in 3..90) return "اسم الخطة يجب أن يحتوي بين 3 و90 حرفًا."
        if (days !in 1..365) return "اختر مدة بين 1 و365 يومًا."
        if (target !in 5..500) return "اختر هدفًا يوميًا من 5 إلى 500 سؤال."
        return null
    }
}
