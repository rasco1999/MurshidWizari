package com.murshidwizari.student

import java.text.Normalizer

/** Conservative practice feedback. Only the server may award official grades or points. */
internal object OfflinePracticeRules {
    enum class Result { CORRECT, INCORRECT, NEEDS_SERVER }

    private fun normalized(value: String): String {
        val digits = "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹"
        val text = Normalizer.normalize(value, Normalizer.Form.NFC)
        return buildString {
            for (c in text) {
                val index = digits.indexOf(c)
                when {
                    index >= 0 -> append(('0'.code + index % 10).toChar())
                    c.isWhitespace() -> append(' ')
                    // Case, Arabic vowels and operators can change the answer.
                    // Leave linguistic equivalence to the authoritative server.
                    else -> append(c)
                }
            }
        }.trim().replace(Regex("\\s+"), " ")
    }

    fun evaluate(type: String, answer: String, expected: String,
                 choices: List<String> = emptyList()): Result {
        val given = normalized(answer)
        val correct = normalized(expected)
        if (given.isEmpty() || correct.isEmpty()) return Result.NEEDS_SERVER
        if (given == correct) return Result.CORRECT
        // Unlike fuzzy text matching, a choice from a validated set is unambiguous.
        if (type == "mcq" || type == "true_false") {
            val available = choices.map(::normalized)
            if (given in available && correct in available) return Result.INCORRECT
        }
        val number = Regex("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
        if (number.matches(given) && number.matches(correct)) {
            return if (given.toBigDecimalOrNull()?.compareTo(correct.toBigDecimalOrNull()) == 0)
                Result.CORRECT else Result.INCORRECT
        }
        // Text, units and symbolic equivalence need the authoritative server engine.
        return Result.NEEDS_SERVER
    }
}
