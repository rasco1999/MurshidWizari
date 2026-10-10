package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class ProposalRulesTest {
    @Test fun validatesMultipleChoiceAndTrueFalseProposals() {
        assertNull(ProposalRules.error(10, "mcq", "من هو المؤلف؟", "أحمد", listOf("أحمد", "خالد")))
        assertNotNull(ProposalRules.error(10, "mcq", "من هو المؤلف؟", "أحمد", listOf("أحمد", "أحمد")))
        assertNotNull(ProposalRules.error(10, "mcq", "من هو المؤلف؟", "علي", listOf("أحمد", "خالد")))
        assertNull(ProposalRules.error(10, "true_false", "هذا صحيح؟", "صح", emptyList()))
        assertNotNull(ProposalRules.error(10, "true_false", "هذا صحيح؟", "نعم", emptyList()))
    }

    @Test fun requiresRealTopicTextAndAnswer() {
        assertNotNull(ProposalRules.error(0, "text", "نص سؤال كامل", "جواب", emptyList()))
        assertNotNull(ProposalRules.error(9, "text", "قصير", "جواب", emptyList()))
        assertNotNull(ProposalRules.error(9, "text", "سؤال كامل", "", emptyList()))
        assertNull(ProposalRules.error(9, "meaning", "معنى العبارة هنا", "المعنى", emptyList()))
    }
}
