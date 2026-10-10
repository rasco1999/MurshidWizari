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

    @Test fun topicWithoutCountRemainsOpenable() {
        val topic = Topic.from(org.json.JSONObject().put("id", 5).put("name", "الموضوع"))
        assertEquals(-1, topic.count)
    }

    @Test fun planKeyMatchesRealServerCheckoutContract() {
        val plan = SubscriptionPlan.from(org.json.JSONObject()
            .put("key", "month1").put("label", "شهر واحد").put("amount", 8000))
        assertEquals("month1", plan.id)
        assertEquals("شهر واحد", plan.label)
        assertEquals(8000, plan.amount)
    }

    @Test fun plansDoNotConfuseDisplayLabelWithCheckoutKey() {
        val json = org.json.JSONObject().put("key", "month3")
            .put("label", "ثلاثة أشهر").put("amount", 15000)
        assertEquals("month3", SubscriptionPlan.from(json).id)
    }

    @Test fun hiddenFrenchIsFilteredForBothArabicAndEnglishNames() {
        val subjects = listOf(
            Subject(1, "اللغة الفرنسية", 12),
            Subject(2, "French", 10),
            Subject(3, "الرياضيات", 30)
        )
        assertEquals(listOf(Subject(3, "الرياضيات", 30)), Subject.visible(subjects))
    }

    @Test fun serverQuizOptionsCanBeStringOrObject() {
        val question = QuizQuestion.from(org.json.JSONObject()
            .put("id", 42).put("text", "اختر الإجابة")
            .put("options", org.json.JSONArray()
                .put(org.json.JSONObject().put("id", 7).put("text", "أول"))
                .put("ثان")))
        assertEquals("7", question.options[0].id)
        assertEquals("أول", question.options[0].title)
        assertEquals("ثان", question.options[1].title)
    }
}
