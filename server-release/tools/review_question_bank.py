"""Read educational tables only. Produce guarded corrections and an honest per-question review log.

Never executes SQL, changes publication/provenance, or treats a machine scan as human proofreading.
"""
import argparse
import collections
import copy
import hashlib
import json
import re
import unicodedata
from pathlib import Path

from audit_education_sql import HEADER, rows

TEXT_FIELDS = ('question_text', 'meaning_word', 'correct_answer', 'explanation',
               'option_a', 'option_b', 'option_c', 'option_d')
JSON_FIELDS = ('genetics_json', 'calculation_json')
HINT_LINE = re.compile(r'^[ \t]*(?:تلميح|تلميحات|hint|hints|clue|tip)[ \t]*[:：][^\n]*(?:\n|$)', re.I | re.M)
HINT_SUFFIX = re.compile(r'[ \t]*[（(](?:تلميح|تلميحات|hint|hints|clue|tip)[ \t]*[:：][^）)\n]*[）)][ \t]*$', re.I)
HINT_KEY = re.compile(r'(^|[_-])(?:hints?|clues?|tips?)($|[_-])', re.I)
WORDS = re.compile(r'[\u0621-\u064a]+')
ORTHOGRAPHY = {
    'إستخدام': 'استخدام', 'إستعمال': 'استعمال', 'إستفادة': 'استفادة',
    'إستمرار': 'استمرار', 'إستطاع': 'استطاع', 'إستنتاج': 'استنتاج',
    'الإحتياط': 'الاحتياط', 'الإبتدائي': 'الابتدائي', 'الإستمرارية': 'الاستمرارية',
    'الإجتماعي': 'الاجتماعي', 'الإرتباط': 'الارتباط', 'الإختفاء': 'الاختفاء',
    'الإطلاع': 'الاطلاع', 'الإطالع': 'الاطلاع', 'الإسم': 'الاسم',
    'اللذي': 'الذي', 'اللتي': 'التي', 'هاذا': 'هذا', 'هاذه': 'هذه',
    'ذالك': 'ذلك', 'لاكن': 'لكن', 'مالمح': 'ملامح',
    'الإسالمية': 'الإسلامية', 'الإسالمي': 'الإسلامي',
}
QUOTED = re.compile(r'(﴿[^﴾]*﴾|«[^»]*»|“[^”]*”|"[^"\n]*")')


def without_inline_hints(value):
    return HINT_SUFFIX.sub('', HINT_LINE.sub('', value.replace('\r\n', '\n'))).strip()


def strip_hints(value, path='$', removed=None):
    removed = [] if removed is None else removed
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            name = re.sub(r'([a-z])([A-Z])', r'\1_\2', key)
            if HINT_KEY.search(name):
                removed.append(path + '.' + key)
            else:
                out[key] = strip_hints(item, path + '.' + key, removed)
        return out
    if isinstance(value, list):
        return [strip_hints(item, path + '[' + str(i) + ']', removed) for i, item in enumerate(value)]
    return value


def orthography_plain(text):
    # Convert Arabic presentation glyphs only; never use NFKC on formulas, exponents or units.
    text = ''.join(unicodedata.normalize('NFKC', ch) if
                   0xFB50 <= ord(ch) <= 0xFD3D or 0xFE70 <= ord(ch) <= 0xFEFC else ch for ch in text)

    def word_fix(match):
        word = match[0]
        # Repair the characteristic displaced lam/alef produced by old Arabic OCR.
        word = re.sub(r'^([وفبكل]{0,2})ا([أإا])ل', r'\1ال\2', word)
        for old, new in ORTHOGRAPHY.items():
            if old in word:
                word = word.replace(old, new)
        return word

    return WORDS.sub(word_fix, text)


def orthography(text):
    # Direct quotations retain their exact source spelling for a teacher to verify.
    return ''.join(part if QUOTED.fullmatch(part) else orthography_plain(part)
                   for part in QUOTED.split(text))


def findings(text):
    out = []
    patterns = {
        'ocr_fragment_needs_source': r'األ|اإل|اال|\bالسالم\b|مااأل',
        'replacement_character': '\ufffd',
        'control_character': r'[\x00-\x08\x0b\x0c\x0e-\x1f]',
        'embedded_verification_or_answer': r'تحقق بصري|تدريب تحقق وزاري|هل الإجابة «|الإجابة عن «',
        'repeated_word_review': r'(?<!\w)([\u0621-\u064a]{2,})\s+\1(?!\w)',
    }
    for name, pattern in patterns.items():
        if re.search(pattern, text):
            out.append(name)
    return out


def literal(value):
    if value is None:
        return 'NULL'
    return "CONVERT(0x" + str(value).encode('utf-8').hex() + " USING utf8mb4)"


def guarded_update(question, changes, reverse=False):
    version = int(question.get('question_version') or 1)
    sets, guards = [], []
    for field, diff in changes.items():
        old, new = (diff['after'], diff['before']) if reverse else (diff['before'], diff['after'])
        sets.append('`' + field + '`=' + literal(new))
        guards.append('(BINARY `' + field + '` <=> BINARY ' + literal(old) + ')')
    expected = version + 1 if reverse else version
    sets.extend(['question_version=' + str(expected + 1), 'content_updated_at=CURRENT_TIMESTAMP'])
    return ('UPDATE questions SET ' + ','.join(sets) + ' WHERE id=' + str(question['id']) +
            ' AND question_version=' + str(expected) + ' AND ' + ' AND '.join(guards) + ';')


def read_education(sql):
    source = sql.read_text(encoding='utf-8-sig')
    tables = collections.defaultdict(list)
    for match in HEADER.finditer(source):
        columns = re.findall(r'`([^`]+)`', match[2])
        for values in rows(source, match.end()):
            if len(columns) != len(values):
                raise ValueError('Invalid educational row length')
            record = dict(zip(columns, values))
            if match[1] == 'questions':
                record.pop('submitted_by', None)
                record.pop('reviewed_scientific_by', None)
                record.pop('reviewed_language_by', None)
            tables[match[1]].append(record)
    return tables


def run(sql, output):
    tables = read_education(sql)
    subjects = {r['id']: r for r in tables['subjects']}
    chapters = {r['id']: r for r in tables['chapters']}
    grades = {r['id']: r for r in tables['grades']}
    options = collections.defaultdict(list)
    for answer in tables['answers']:
        options[answer['question_id']].append(answer['answer_text'])
    reports, statements, rollback = [], [], []
    by_topic = collections.defaultdict(collections.Counter)
    all_removed = 0
    for question in tables['questions']:
        q = copy.deepcopy(question)
        chapter = chapters.get(q.get('chapter_id'), {})
        subject = subjects.get(chapter.get('subject_id'), {})
        grade = grades.get(subject.get('grade_id'), {})
        protected = 'إسلام' in subject.get('name', '') or 'اسلام' in subject.get('name', '') or q.get('type') == 'match'
        changes, notes, removed = {}, [], []
        for field in TEXT_FIELDS:
            before = q.get(field)
            if not isinstance(before, str) or not before:
                continue
            candidate = before if protected else orthography(before)
            # Answers and choices stay paired with the existing grading rules. Record proposals only.
            if field in ('question_text', 'explanation'):
                candidate = without_inline_hints(candidate)
                if candidate != before:
                    changes[field] = {'before': before, 'after': candidate}
                    q[field] = candidate
            elif candidate != before:
                notes.append({'field': field, 'kind': 'answer_or_option_spelling_proposal', 'proposed': candidate})
            for issue in findings(candidate):
                notes.append({'field': field, 'kind': issue})
        for field in JSON_FIELDS:
            before = q.get(field)
            if not before:
                continue
            try:
                definition = json.loads(before)
            except (ValueError, TypeError):
                notes.append({'field': field, 'kind': 'invalid_json'})
                continue
            cleaned = strip_hints(definition, field, removed)
            if cleaned != definition:
                after = json.dumps(cleaned, ensure_ascii=False, separators=(',', ':'))
                changes[field] = {'before': before, 'after': after}
                q[field] = after
        # A bank record is accounted for even when it has no machine-detected problem.
        published = str(q.get('status')) == '1' and q.get('moderation_status') in (None, 'approved')
        item = {
            'id': q['id'], 'grade': grade.get('name', ''), 'subject': subject.get('name', ''),
            'chapter': chapter.get('name', ''), 'chapter_id': q.get('chapter_id'), 'type': q['type'],
            'published': published, 'text': q['question_text'], 'correct_answer': q['correct_answer'],
            'explanation': q.get('explanation') or '', 'options': options[q['id']],
            'changes': changes, 'removed_hint_paths': removed, 'findings': notes,
            'protected_scriptural_fields': protected,
            'automated_text_scan': 'completed', 'human_language_review': 'not_completed',
            'native_visual_review': 'not_completed', 'official_source_verified': False,
        }
        reports.append(item)
        total = by_topic[(item['grade'], item['subject'], item['chapter'], item['chapter_id'])]
        total['questions'] += 1
        total['published'] += int(published)
        total['changed_questions'] += int(bool(changes))
        total['flagged_questions'] += int(bool(notes))
        total['hint_keys_removed'] += len(removed)
        all_removed += len(removed)
        if changes:
            statements.append(guarded_update(question, changes))
            rollback.append(guarded_update(question, changes, reverse=True))
        assert q['correct_answer'] == question['correct_answer']
        for field in ('option_a', 'option_b', 'option_c', 'option_d'):
            assert q.get(field) == question.get(field)
    summary = {
        'source_sha256': hashlib.sha256(sql.read_bytes()).hexdigest(),
        'source_filename': sql.name, 'source_export_date': '2026-10-14',
        'question_records_scanned': len(reports), 'published_questions_scanned': sum(r['published'] for r in reports),
        'topics_scanned': len(by_topic), 'changed_question_count': len(statements),
        'removed_hint_key_count': all_removed,
        'questions_with_review_findings': sum(bool(r['findings']) for r in reports),
        'human_language_review_complete': False, 'individual_native_visual_review_complete': False,
        'scientific_verification_complete': False, 'live_database_modified': False,
        'correct_answers_modified': False, 'publication_or_provenance_modified': False,
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / 'per_question_review.json').write_text(json.dumps(reports, ensure_ascii=False, separators=(',', ':')))
    (output / 'review_summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    topics = [dict(zip(('grade', 'subject', 'chapter', 'chapter_id'), key), **counts) for key, counts in by_topic.items()]
    (output / 'per_topic_review.json').write_text(json.dumps(topics, ensure_ascii=False, indent=2))
    header = '-- Generated from the supplied snapshot. UPDATE only: no INSERT, DELETE, publication or provenance changes.\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n'
    (output / 'apply_guarded_corrections.sql').write_text(header + '\n'.join(statements) + '\nCOMMIT;\n')
    (output / 'rollback_guarded_corrections.sql').write_text(header + '\n'.join(rollback) + '\nCOMMIT;\n')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('sql', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args.sql, args.output), ensure_ascii=False))
