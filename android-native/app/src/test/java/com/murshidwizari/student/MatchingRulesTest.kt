package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class MatchingRulesTest {
    private val def = MatchingRules.Definition(mapOf("1" to "x-ray", "2" to "security"),mapOf("a" to "camera", "b" to "machine"))
    @Test fun partialAndDuplicateAnswersCannotSubmit() {
        assertFalse(MatchingRules.complete(def,"1-b"))
        assertFalse(MatchingRules.complete(def,"1-b, 2-b"))
        assertFalse(MatchingRules.complete(def,"1-b, 1-a"))
        assertFalse(MatchingRules.complete(def,"1-b, 2-z"))
        assertFalse(MatchingRules.complete(def,"1-b, 2-a junk"))
        assertTrue(MatchingRules.complete(def,"2-a, 1-b"))
    }
    @Test fun editingAnOccupiedTargetRemovesThePreviousLink() {
        val changed=MatchingRules.assign(mapOf("1" to "b", "2" to "a"),"2","b")
        assertEquals(mapOf("2" to "b"),changed)
        assertFalse(MatchingRules.complete(def,MatchingRules.encode(changed)))
    }
    @Test fun dailyChallengeRequiresEveryPairAndKeepsDefinitionInDraftIdentity() {
        val q=CompetitionRules.Question(53,"text","صِل عناصر القائمتين", emptyList(),def)
        assertNull(CompetitionRules.issue(listOf(q)))
        assertFalse(CompetitionRules.canSubmit(listOf(q),mapOf(53 to "1-b"),true,false,false,false))
        assertTrue(CompetitionRules.canSubmit(listOf(q),mapOf(53 to "1-b, 2-a"),true,false,false,false))
        val changed=q.copy(matching=def.copy(right=mapOf("a" to "camera changed", "b" to "machine")))
        assertNotEquals(DailyDraftRules.signature(1,listOf(q)),DailyDraftRules.signature(1,listOf(changed)))
    }
    @Test fun preservesOriginalTrueFalseButRejectsGeneratedValidation() {
        assertFalse(MatchingRules.generated("اسم المفعول يدل على من وقع عليه الفعل."))
        assertTrue(MatchingRules.generated("هل العبارة التالية صحيحة أم خاطئة؟ الإجابة المناسبة للسؤال «Match» هي «1-a»."))
    }
}
