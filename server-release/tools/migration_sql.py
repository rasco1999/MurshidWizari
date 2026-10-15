"""Generate version-guarded educational updates and a guarded, cache-safe rollback."""
import hashlib,json
from pathlib import Path

CODE='question_presentation_v53_20261015'
BACKUP='murshid_question_review_v53_backup'
def lit(v):
    return 'NULL' if v is None else "CONVERT(0x"+str(v).encode('utf-8').hex()+" USING utf8mb4)"
def digest(q):
    fields=[q.get('type','text'),str(q.get('chapter_id')) if q.get('chapter_id') is not None else '<NULL>',q.get('question_text') or '',q.get('correct_answer') or '',q.get('explanation') if q.get('explanation') is not None else '<NULL>',str(q.get('status',1)),str(q.get('question_version',1))]
    assert all('\x1f' not in str(x) for x in fields)
    return hashlib.sha256('\x1f'.join(map(str,fields)).encode()).hexdigest()
def source_digest(q):return hashlib.sha256((q['question_text']+'\x1f'+q['correct_answer']).encode()).hexdigest()
def expr(alias):
    return f"SHA2(CONCAT_WS(CHAR(31),{alias}.type,COALESCE(CAST({alias}.chapter_id AS CHAR),'<NULL>'),{alias}.question_text,{alias}.correct_answer,COALESCE({alias}.explanation,'<NULL>'),CAST({alias}.status AS CHAR),CAST({alias}.question_version AS CHAR)),256)"

def apply_sql(changes,by_id):
    rows=[]
    for c in changes:
        before=c['before'];after=dict(c['after']);after['question_version']=int(before.get('question_version') or 1)+1
        retained=by_id.get(c.get('retained_question_id'))
        rows.append('('+','.join([str(c['id']),lit(digest(before)),lit(digest(after)),
            lit(after['question_text']) if after['question_text']!=before['question_text'] else 'NULL',
            lit(after.get('explanation')) if after.get('explanation')!=before.get('explanation') else 'NULL',
            str(int(after.get('explanation')!=before.get('explanation'))),str(after['status']),
            lit(source_digest(retained)) if retained else 'NULL'])+')')
    schema=f'''SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS {BACKUP} (
 migration_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question_id INT UNSIGNED NOT NULL,
 before_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 applied_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 before_text MEDIUMTEXT NOT NULL, before_explanation MEDIUMTEXT NULL,
 before_status TINYINT NOT NULL, before_version INT NOT NULL, before_updated_at DATETIME NULL,
 applied_text MEDIUMTEXT NOT NULL, applied_explanation MEDIUMTEXT NULL,
 applied_status TINYINT NOT NULL, applied_version INT NOT NULL,
 backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(migration_code,question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DROP TEMPORARY TABLE IF EXISTS _murshid_v53_candidates;
CREATE TEMPORARY TABLE _murshid_v53_candidates (
 id INT UNSIGNED PRIMARY KEY,
 before_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 applied_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 new_text MEDIUMTEXT NULL, new_explanation MEDIUMTEXT NULL,
 change_explanation TINYINT NOT NULL, new_status TINYINT NOT NULL,
 retained_source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
'''
    inserts='\n'.join('INSERT INTO _murshid_v53_candidates VALUES\n'+',\n'.join(rows[i:i+200])+';' for i in range(0,len(rows),200))
    return '-- Partial educational update only. No users, payments, scores or answer events are changed.\n'+schema+inserts+f'''
START TRANSACTION;
INSERT INTO {BACKUP}(migration_code,question_id,before_hash,applied_hash,before_text,before_explanation,before_status,before_version,before_updated_at,applied_text,applied_explanation,applied_status,applied_version)
SELECT '{CODE}',q.id,c.before_hash,c.applied_hash,q.question_text,q.explanation,q.status,q.question_version,q.content_updated_at,
 COALESCE(c.new_text,q.question_text),IF(c.change_explanation=1,c.new_explanation,q.explanation),c.new_status,q.question_version+1
FROM questions q JOIN _murshid_v53_candidates c ON c.id=q.id
LEFT JOIN {BACKUP} b ON b.question_id=q.id AND b.migration_code='{CODE}'
WHERE b.question_id IS NULL AND {expr('q')}=c.before_hash
 AND (c.retained_source_hash IS NULL OR EXISTS(
 SELECT 1 FROM questions original WHERE original.chapter_id=q.chapter_id AND original.status=1
 AND SHA2(CONCAT_WS(CHAR(31),original.question_text,original.correct_answer),256)=c.retained_source_hash));
UPDATE questions q JOIN {BACKUP} b ON b.question_id=q.id AND b.migration_code='{CODE}'
SET q.question_text=b.applied_text,q.explanation=b.applied_explanation,q.status=b.applied_status,
 q.question_version=b.applied_version,q.content_updated_at=NOW()
WHERE {expr('q')}=b.before_hash;
SELECT ROW_COUNT() AS updated_questions_this_run;
COMMIT;
SELECT (SELECT COUNT(*) FROM _murshid_v53_candidates) AS proposed_questions,
 COUNT(*) AS matching_applied_questions FROM {BACKUP} b JOIN questions q ON q.id=b.question_id
 WHERE b.migration_code='{CODE}' AND {expr('q')}=b.applied_hash;
DROP TEMPORARY TABLE _murshid_v53_candidates;
'''

def rollback_sql():
    return f'''-- Restores only rows still identical to this update; later edits are preserved.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE questions q JOIN {BACKUP} b ON b.question_id=q.id AND b.migration_code='{CODE}'
SET q.question_text=b.before_text,q.explanation=b.before_explanation,q.status=b.before_status,
 q.question_version=q.question_version+1,q.content_updated_at=NOW()
WHERE {expr('q')}=b.applied_hash;
SELECT ROW_COUNT() AS restored_questions_this_run;
COMMIT;
'''

if __name__=='__main__':
    root=Path(__file__).resolve().parent
    data=json.loads((root/'source_tables.json').read_text());changes=json.loads((root/'changes.json').read_text())
    (root/'apply_guarded_question_review_v53.sql').write_text(apply_sql(changes,{q['id']:q for q in data['questions']}))
    (root/'rollback_guarded_question_review_v53.sql').write_text(rollback_sql())
    print('Generated',len(changes),'guarded updates with backups and version-increasing rollback')
