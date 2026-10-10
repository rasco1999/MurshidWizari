package com.muriq.murshid.iosparity.notifications

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudentNoticeTest {
    @Test fun preservesServerUnreadAndSorts() {
        val rows = JSONArray()
            .put(JSONObject().put("id", 9).put("title", "الأول").put("body", "نص")
                .put("is_read", 1).put("created_at", "2026-10-11"))
            .put(JSONObject().put("id", 11).put("title", "الثاني").put("body", "تفاصيل")
                .put("is_read", "0"))
        val inbox = NoticeInbox.from(JSONObject().put("unread", 1).put("items", rows))
        assertEquals(1, inbox.unread)
        assertEquals(listOf(11, 9), inbox.items.map { it.id })
        assertFalse(inbox.items[0].read)
        assertTrue(inbox.items[1].read)
    }

    @Test fun ignoresInvalidIdsAndDuplicateItems() {
        val rows = JSONArray()
            .put(JSONObject().put("id", 0))
            .put(JSONObject().put("id", 14).put("is_read", false))
            .put(JSONObject().put("id", 14).put("is_read", false))
        val inbox = NoticeInbox.from(JSONObject().put("items", rows))
        assertEquals(1, inbox.items.size)
        assertEquals(14, inbox.items.first().id)
        assertEquals(1, inbox.unread)
    }

    @Test fun handlesBooleanStringAndNumberFlags() {
        val rows = JSONArray()
            .put(JSONObject().put("id", 1).put("is_read", true))
            .put(JSONObject().put("id", 2).put("is_read", "true"))
            .put(JSONObject().put("id", 3).put("is_read", 0))
        val inbox = NoticeInbox.from(JSONObject().put("items", rows))
        assertEquals(1, inbox.unread)
        assertTrue(inbox.items.first { it.id == 1 }.read)
        assertTrue(inbox.items.first { it.id == 2 }.read)
        assertFalse(inbox.items.first { it.id == 3 }.read)
    }
}
