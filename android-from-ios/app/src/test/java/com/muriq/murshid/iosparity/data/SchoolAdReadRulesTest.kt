package com.muriq.murshid.iosparity.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SchoolAdReadRulesTest {
    @Test fun unchangedContentKeepsRevision() {
        val ad = JSONObject().put("id", 12).put("school", "مدرسة الفجر").put("text", "إعلان جديد")
        assertEquals(SchoolAdReadRules.revision(ad), SchoolAdReadRules.revision(JSONObject(ad.toString())))
    }

    @Test fun editingAdCausesFreshUnreadRevision() {
        val a = JSONObject().put("id", 12).put("school", "مدرسة الفجر").put("text", "إعلان جديد")
        val b = JSONObject(a.toString()).put("text", "إعلان محدّث")
        assertNotEquals(SchoolAdReadRules.revision(a), SchoolAdReadRules.revision(b))
    }
}
