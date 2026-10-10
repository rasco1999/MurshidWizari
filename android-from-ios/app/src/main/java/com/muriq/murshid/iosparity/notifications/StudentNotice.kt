package com.muriq.murshid.iosparity.notifications

import org.json.JSONObject

/** Backend contract: mobile/notifications.php (student-owned, latest 50). */
data class StudentNotice(
    val id: Int,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: String
)

data class NoticeInbox(val unread: Int, val items: List<StudentNotice>) {
    companion object {
        fun from(json: JSONObject): NoticeInbox {
            val array = json.optJSONArray("items")
            val rows = mutableListOf<StudentNotice>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    val id = row.optInt("id")
                    if (id <= 0) continue
                    val read = when (val raw = row.opt("is_read")) {
                        is Boolean -> raw
                        is Number -> raw.toInt() != 0
                        is String -> raw == "1" || raw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    rows.add(
                        StudentNotice(
                            id,
                            row.optString("title").take(180),
                            row.optString("body").take(2000),
                            read,
                            row.optString("created_at").take(60)
                        )
                    )
                }
            }
            val distinct = rows.distinctBy { it.id }.sortedByDescending { it.id }
            return NoticeInbox(json.optInt("unread", distinct.count { !it.read }).coerceAtLeast(0), distinct)
        }
    }
}
