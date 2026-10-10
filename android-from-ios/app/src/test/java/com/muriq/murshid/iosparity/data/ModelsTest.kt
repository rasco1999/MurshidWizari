package com.muriq.murshid.iosparity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelsTest {
    @Test fun frenchIsHiddenAndLiteratureFollowsArabic() {
        val original = listOf(
            Subject(1, "الأدب والنصوص", 12),
            Subject(2, "الرياضيات", 30),
            Subject(3, "اللغة العربية", 40),
            Subject(4, "اللغة الفرنسية", 5)
        )
        val visible = Subject.visible(original)
        assertFalse(visible.any { it.name.contains("الفرنسية") })
        assertEquals("اللغة العربية", visible[1].name)
        assertEquals("الأدب والنصوص", visible[2].name)
    }

    @Test fun topicDefaultsToZeroCount() {
        val topic = Topic.from(org.json.JSONObject().put("id", 5).put("name", "الموضوع"))
        assertEquals(0, topic.count)
    }
}
