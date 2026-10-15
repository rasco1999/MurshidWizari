"""Read educational INSERT statements only. Never execute SQL or inspect student tables."""
import argparse
import collections
import hashlib
import json
import re
import unicodedata
from pathlib import Path

HEADER = re.compile(r"INSERT INTO `(questions|chapters|subjects|grades|answers)`\s*\(([^)]+)\)\s*VALUES\s*", re.I)


def rows(text, start):
    position, length = start, len(text)
    escapes = {'n': '\n', 'r': '\r', 't': '\t', '0': '\0', 'b': '\b', 'Z': '\x1a'}
    while position < length:
        while text[position].isspace() or text[position] == ',': position += 1
        if text[position] == ';': return
        if text[position] != '(': raise ValueError('Malformed educational INSERT')
        position += 1; row = []
        while True:
            while text[position].isspace(): position += 1
            if text[position] == "'":
                position += 1; result = []
                while position < length:
                    character = text[position]; position += 1
                    if character == '\\':
                        character = text[position]; position += 1
                        result.append(escapes.get(character, character))
                    elif character == "'":
                        if text[position:position+1] == "'": result.append("'"); position += 1
                        else: break
                    else: result.append(character)
                value = ''.join(result)
            else:
                end = position
                while text[end] not in ',)': end += 1
                literal = text[position:end].strip(); position = end
                value = None if literal.upper() == 'NULL' else int(literal) if re.fullmatch(r'-?\d+', literal) else literal
            row.append(value)
            while text[position].isspace(): position += 1
            if text[position] == ')': position += 1; yield row; break
            if text[position] != ',': raise ValueError('Malformed educational value list')
            position += 1


def key(value):
    return ' '.join(unicodedata.normalize('NFC', str(value or '')).split()).casefold()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('sql', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    text = args.sql.read_text(encoding='utf-8-sig')
    tables = collections.defaultdict(list)
    for match in HEADER.finditer(text):
        columns = re.findall(r'`([^`]+)`', match[2])
        for row in rows(text, match.end()):
            if len(row) != len(columns): raise ValueError('Column count mismatch in '+match[1])
            tables[match[1]].append(dict(zip(columns, row)))
    subjects = {r['id']: r for r in tables['subjects']}
    chapters = {r['id']: r for r in tables['chapters']}
    options = collections.defaultdict(list)
    for row in tables['answers']: options[row['question_id']].append(row)
    findings = []; published = []; duplicates = collections.defaultdict(list); counts = collections.Counter()
    for q in tables['questions']:
        chapter = chapters.get(q.get('chapter_id')); subject = subjects.get((chapter or {}).get('subject_id'))
        issues = []
        if not chapter or not subject: issues.append('missing_curriculum_relation')
        if 'فرن' in (subject or {}).get('name', '') or 'french' in key((subject or {}).get('name')): continue
        if str(q.get('status', 1)) != '1' or q.get('moderation_status') not in (None, 'approved'): continue
        published.append(q); counts[q.get('chapter_id')] += 1
        if not key(q.get('question_text')): issues.append('empty_question')
        if not key(q.get('correct_answer')): issues.append('empty_answer')
        if not key(q.get('explanation')): issues.append('no_detailed_explanation')
        elif key(q.get('explanation')) == key(q.get('correct_answer')): issues.append('explanation_repeats_answer')
        if q.get('type') == 'mcq' and len(options[q['id']]) < 2: issues.append('incomplete_mcq_options')
        for field in ('calculation_json', 'genetics_json'):
            if q.get(field):
                try: json.loads(q[field])
                except (ValueError, TypeError): issues.append('invalid_'+field)
        option_key = tuple(sorted(key(o.get('answer_text')) for o in options[q['id']]))
        signature = ((subject or {}).get('id'), key((chapter or {}).get('name')),
                     q.get('type'), key(q.get('question_text')), key(q.get('correct_answer')), option_key)
        duplicates[signature].append(q['id'])
        if issues: findings.append({'question_id': q['id'], 'chapter_id': q.get('chapter_id'), 'issues': issues})
    groups = [ids for ids in duplicates.values() if len(ids) > 1]
    args.output.mkdir(parents=True, exist_ok=True)
    metrics = {'source_sha256': hashlib.sha256(args.sql.read_bytes()).hexdigest(),
               'educational_rows': {name: len(value) for name, value in tables.items()},
               'published_non_french_questions': len(published), 'topics_with_published_questions': len(counts),
               'topics_below_100': sum(count < 100 for count in counts.values()),
               'issue_counts': dict(collections.Counter(issue for item in findings for issue in item['issues'])),
               'exact_duplicate_candidate_groups': len(groups),
               'exact_duplicate_candidate_surplus': sum(len(group)-1 for group in groups),
               'database_modified': False, 'scientific_answers_verified_against_official_sources': False}
    (args.output/'education_audit_summary.json').write_text(json.dumps(metrics, ensure_ascii=False, indent=2))
    (args.output/'education_review_queue.json').write_text(json.dumps({'questions': findings, 'duplicate_candidates': groups}, ensure_ascii=False, indent=2))
    print(json.dumps(metrics, ensure_ascii=False))


if __name__ == '__main__':
    main()
