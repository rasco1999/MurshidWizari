package com.murshidwizari.student

/** Display-only protection for previously cached prompts. Never alters grading or revisions. */
object QuestionPresentation {
    const val BADGE = "وزاري"
    private val hintLine = Regex(
        "(?im)^[ \\t]*(?:تلميح|تلميحات|hint|hints|clue|tip)[ \\t]*[:：][^\\n]*(?:\\n|$)"
    )
    private val hintSuffix = Regex(
        "(?i)[ \\t]*[（(](?:تلميح|تلميحات|hint|hints|clue|tip)[ \\t]*[:：][^）)\\n]*[）)][ \\t]*$"
    )
    fun prompt(raw: String): String = raw.replace("\r\n", "\n")
        .replace(hintLine, "").replace(hintSuffix, "").trim()
}
