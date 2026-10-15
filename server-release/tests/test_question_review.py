import importlib.util
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import review_question_bank as review


class ContentReviewRules(unittest.TestCase):
    def test_cached_hint_blocks_do_not_change_the_question(self):
        self.assertEqual(review.without_inline_hints('احسب 2 + 3.\nتلميح: الناتج خمسة'), 'احسب 2 + 3.')
        self.assertEqual(review.without_inline_hints('Find x: x² = 4.\nHint: x = 2'), 'Find x: x² = 4.')
        self.assertEqual(review.without_inline_hints('ما معنى كلمة تلميح؟'), 'ما معنى كلمة تلميح؟')

    def test_recursive_hint_filter_preserves_scoring_and_problem_keys(self):
        original = {'stages': [{'key': 'x', 'kind': 'number', 'label': 'x²', 'hint': '5',
                                 'wrong_hint': '5', 'hintText': '5', 'correct': '4', 'unit': 'cm²'}],
                    'answer': {'stages': {'x': '4'}}}
        removed = []
        cleaned = review.strip_hints(original, removed=removed)
        self.assertEqual(len(removed), 3)
        self.assertEqual(cleaned['stages'][0], {'key': 'x', 'kind': 'number', 'label': 'x²', 'correct': '4', 'unit': 'cm²'})
        self.assertEqual(cleaned['answer'], original['answer'])
        self.assertIn('hint', original['stages'][0])

    def test_ocr_repairs_leave_math_verses_and_direct_quotes_intact(self):
        self.assertEqual(review.orthography('الدور األول مع إستخدام طريقة الحل'), 'الدور الأول مع استخدام طريقة الحل')
        for text in ['P ≠ p؛ 10²؛ 10^2؛ 1,5؛ عِلْم', '﴿قُلْ هُوَ اللَّهُ أَحَدٌ﴾', '«األول»', 'H₂ + O₂ → H₂O', 'قال رسول الله ﷺ']:
            self.assertEqual(review.orthography(text), text)

    def test_migration_is_guarded_and_does_not_recreate_deleted_questions(self):
        q = {'id': 42, 'question_version': 3}
        changes = {'question_text': {'before': 'الدور األول', 'after': 'الدور الأول'}}
        sql = review.guarded_update(q, changes)
        self.assertIn('WHERE id=42 AND question_version=3', sql)
        self.assertIn('BINARY `question_text`', sql)
        self.assertIn('question_version=4', sql)
        self.assertNotIn('INSERT', sql)
        self.assertNotIn('correct_answer', sql)
        self.assertIn('question_version=5', review.guarded_update(q, changes, reverse=True))

    def test_empty_and_null_sql_values_are_distinct(self):
        self.assertEqual(review.literal(''), "CONVERT(X'' USING utf8mb4)")
        self.assertEqual(review.literal(None), 'NULL')


if __name__ == '__main__':
    unittest.main()
