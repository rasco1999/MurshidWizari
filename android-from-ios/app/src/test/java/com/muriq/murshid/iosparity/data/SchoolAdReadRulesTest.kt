package com.muriq.murshid.iosparity.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SchoolAdReadRulesTest {
    @Test fun unchangedContentKeepsRevision() {
        val ad = JSONObject().put("id", 12).put("school", "مدرسة الفجر").put("text", "إعلان جديد")
        assertEquals(SchoolAdReadRules.revision(ad), SchoolAdReadRules.revision(JSONObject(ad.toString())))
    }

    @Test fun onlyPublishedUniqueSchoolAdsAppearAsUnread() {
        val enabled = JSONObject().put("id", 12).put("school", "مدرسة الفجر")
            .put("text", "إعلان جديد").put("is_enabled", true)
        val duplicate = JSONObject(enabled.toString())
        val disabled = JSONObject().put("id", 13).put("school", "مدرسة الربيع")
            .put("text", "إعلان معطل").put("is_enabled", false)
        val inactive = JSONObject().put("id", 14).put("school", "مدرسة الربيع")
            .put("text", "إعلان قديم").put("active", false)
        val missingText = JSONObject().put("id", 15).put("school", "مدرسة الربيع")
        assertEquals(listOf(enabled), SchoolAdReadRules.published(
            listOf(enabled, duplicate, disabled, inactive, missingText)
        ))
    }

    @Test fun editingAdCausesFreshUnreadRevision() {
        val a = JSONObject().put("id", 12).put("school", "مدرسة الفجر").put("text", "إعلان جديد")
        val b = JSONObject(a.toString()).put("text", "إعلان محدّث")
        assertNotEquals(SchoolAdReadRules.revision(a), SchoolAdReadRules.revision(b))
    }
}

