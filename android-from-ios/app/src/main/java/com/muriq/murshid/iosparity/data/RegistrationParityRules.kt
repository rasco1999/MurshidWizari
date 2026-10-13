package com.muriq.murshid.iosparity.data

/** Client validation for the three-step native registration; backend remains authoritative. */
object RegistrationParityRules {
    fun personalError(name: String, gradeId: Int): String? {
        if (name.trim().split(Regex("\\s+")).filter(String::isNotBlank).size < 3)
            return "أدخل الاسم الثلاثي الكامل."
        if (gradeId <= 0) return "اختر الصف الدراسي."
        return null
    }

    fun contactError(value: String, type: String): String? {
        val trimmed = value.trim()
        if (type !in setOf("email", "phone")) return "وسيلة التواصل غير صالحة."
        if (type == "email" && (!trimmed.contains('@') || trimmed.startsWith("@") ||
            !trimmed.substringAfterLast('@').contains('.')))
            return "أدخل عنوان بريد إلكتروني صحيحًا."
        if (type == "phone" && !trimmed.matches(Regex("(?:0|\\+964)?7[0-9]{9}")))
            return "أدخل رقم هاتف عراقيًا صحيحًا."
        return null
    }

    fun passwordError(password: String, confirm: String): String? {
        if (password.length < 8) return "كلمة المرور لا تقل عن 8 أحرف."
        if (password != confirm) return "كلمتا المرور غير متطابقتين."
        return null
    }
}
