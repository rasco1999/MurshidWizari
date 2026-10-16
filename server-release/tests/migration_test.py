"""Exercise the actual SQL generator against an isolated, synthetic MySQL database."""
import copy,json,subprocess,sys,os
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from migration_sql import apply_sql,rollback_sql,lit

DB='murshid_matching_migration_test'
COLLATION=os.environ.get('MIGRATION_TEST_COLLATION','utf8mb4_general_ci')
assert COLLATION in ['utf8mb4_general_ci','utf8mb4_unicode_ci','utf8mb4_bin']
CMD=['mysql','--host=127.0.0.1','--user=root','--password=synthetic-ci-only','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names']
def run(sql):
    return subprocess.run(CMD,input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout.decode()
def query(sql):return run('USE '+DB+';'+sql).strip()
def row(i,text='سؤال اصطناعي',answer='8',status=1,version=1,explanation=None):
    return dict(id=i,chapter_id=1,type='text',question_text=text,correct_answer=answer,explanation=explanation,status=status,question_version=version,content_updated_at=None)
original=row(10,'اكتب اسم العضو المسؤول عن الإبصار.','العين')
before=[row(1,'الإجابة المناسبة للسؤال «سؤال اصطناعي» هي «8».','صح'),row(2,'اين المدرسة؟'),row(3,'سؤال تعدّل بالتزامن'),row(4,'حُذف لاحقاً'),row(5,'سؤال شرح',explanation='الى المدرسة'),row(6,'مراجعة: سؤال متوقف',status=0),row(7,'الإجابة المناسبة للسؤال «مرجع مفقود» هي «8».'),row(8,"O'Brien\nمسألة: x² − 2x = 0",'x = 0 أو 2')]
changes=[]
for q in before:
    after=copy.deepcopy(q)
    if q['id'] in [1,7]:after['status']=0
    elif q['id']==2:after['question_text']='أين المدرسة؟'
    elif q['id']==5:after['explanation']='إلى المدرسة'
    elif q['id']==6:after['question_text']='سؤال متوقف'
    elif q['id']==8:after['question_text']="O'Brien\nمسألة: x² − 2x = 0؟"
    else:after['status']=0
    changes.append({'id':q['id'],'before':q,'after':after,'retained_question_id':10 if q['id']==1 else 99 if q['id']==7 else None})
allrows={q['id']:q for q in before+[original,row(99,'أصل غائب')]}  # 99 deliberately never inserted.
setup=f'''DROP DATABASE IF EXISTS {DB}; CREATE DATABASE {DB} CHARACTER SET utf8mb4 COLLATE {COLLATION}; USE {DB};
CREATE TABLE questions(id INT PRIMARY KEY,chapter_id INT NULL,type VARCHAR(30),question_text MEDIUMTEXT,correct_answer MEDIUMTEXT,explanation MEDIUMTEXT NULL,status TINYINT,question_version INT,content_updated_at DATETIME NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
'''
for q in before+[original]:
    if q['id']==4:continue
    live=dict(q)
    if live['id']==3:live['question_version']=2;live['question_text']='تعديل أحدث يجب حفظه'
    setup+='INSERT INTO questions VALUES('+','.join([str(live['id']),'1',lit(live['type']),lit(live['question_text']),lit(live['correct_answer']),lit(live['explanation']),str(live['status']),str(live['question_version']),'NULL'])+');\n'
run(setup)
sql=apply_sql(changes,allrows)
# Simulate the previous import stopping after its DDL and staging inserts.
# Its persistent backup table must be reusable, without deleting recovery data.
run('USE '+DB+';'+sql.split('START TRANSACTION;',1)[0])
assert query('SELECT COUNT(*) FROM murshid_question_review_v53_backup;')=='0'
run('USE '+DB+';'+sql)
def state():return {q['id']:q for q in json.loads(query("SELECT JSON_ARRAYAGG(JSON_OBJECT('id',id,'text',question_text,'explanation',explanation,'status',status,'version',question_version,'answer',correct_answer)) FROM questions;"))}
s=state();assert s[1]['status']==0;assert s[2]['text']=='أين المدرسة؟';assert s[2]['version']==2
assert s[3]['text']=='تعديل أحدث يجب حفظه' and s[3]['version']==2
assert 4 not in s;assert s[5]['explanation']=='إلى المدرسة';assert s[6]['status']==0 and s[6]['version']==2
assert s[7]['status']==1;assert s[8]['answer']=='x = 0 أو 2';assert s[10]['text']==original['question_text']
run('USE '+DB+';'+sql);assert state()==s  # Repeat application changes nothing.
run('USE '+DB+";UPDATE questions SET question_text='تصحيح أحدث بعد التحديث',question_version=question_version+1 WHERE id=2;")
run('USE '+DB+';'+rollback_sql());restored=state()
assert restored[1]['status']==1 and restored[1]['version']==3
assert restored[2]['text']=='تصحيح أحدث بعد التحديث' and restored[2]['version']==3
assert restored[5]['explanation']=='الى المدرسة' and restored[5]['version']==3
assert restored[8]['text']==before[-1]['question_text'] and restored[8]['version']==3
run('USE '+DB+';'+rollback_sql());assert state()==restored
run('USE '+DB+';'+sql);assert state()==restored
print('PASS 18 real SQL migration assertions ('+COLLATION+'): stopped-import recovery, guards, UTF-8, idempotency, newer edits and rollback')
