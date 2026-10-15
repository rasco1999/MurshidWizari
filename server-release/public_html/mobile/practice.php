<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
require_once __DIR__.'/../includes/platform_tools.php';
$u=mobile_student();$pdo=db();platform_tools_schema($pdo);$uid=(int)$u['id'];$grade=(int)$u['grade_id'];

function practice_supported_type(string $type): bool { return in_array($type,['mcq','true_false','text','fill','meaning'],true); }
function practice_state_ok(int $qid,string $state,array $answered,array $favorite): bool {
    if($state==='unanswered')return !isset($answered[$qid]);
    if($state==='wrong')return isset($answered[$qid])&&!$answered[$qid];
    if($state==='correct')return isset($answered[$qid])&&$answered[$qid];
    if($state==='favorite')return isset($favorite[$qid]);
    return true;
}

if($_SERVER['REQUEST_METHOD']==='POST'){
    $data=json_decode((string)file_get_contents('php://input'),true);if(!is_array($data)||!verify_csrf($data['csrf']??null))json_response(['ok'=>false,'message'=>'انتهت صلاحية الطلب.'],419);
    $action=(string)($data['action']??'');
    if($action==='create'){
        if(!rate_limit('custom-exam-create',12,3600))json_response(['ok'=>false,'message'=>'أنشأت اختبارات كثيرة خلال وقت قصير. حاول بعد قليل.'],429);
        $subjectId=(int)($data['subject_id']??0);$count=(int)($data['count']??10);$type=trim((string)($data['type']??'all'));$state=trim((string)($data['state']??'all'));$minutes=(int)($data['minutes']??0);
        $chapterIds=array_values(array_unique(array_filter(array_map('intval',(array)($data['chapter_ids']??[])),static fn($v)=>$v>0)));
        if(!in_array($type,['all','mcq','true_false','text','fill','meaning'],true))$type='all';if(!in_array($state,['all','unanswered','wrong','correct','favorite'],true))$state='all';
        $q=$pdo->prepare("SELECT id,name FROM subjects WHERE id=? AND grade_id=? AND status=1 AND name NOT LIKE '%فرن%' AND LOWER(name) NOT LIKE '%french%'");$q->execute([$subjectId,$grade]);$subject=$q->fetch(PDO::FETCH_ASSOC);if(!$subject)json_response(['ok'=>false,'message'=>'المادة غير متاحة.'],404);
        if($chapterIds){$marks=implode(',',array_fill(0,count($chapterIds),'?'));$chk=$pdo->prepare("SELECT id FROM chapters WHERE id IN ($marks) AND subject_id=? AND status=1");$chk->execute(array_merge($chapterIds,[$subjectId]));$chapterIds=array_map('intval',$chk->fetchAll(PDO::FETCH_COLUMN));if(!$chapterIds)json_response(['ok'=>false,'message'=>'المواضيع المختارة غير متاحة.'],422);}
        $remaining=is_subscribed($uid)?50:free_answers_remaining($uid);if($remaining<1)json_response(['ok'=>false,'payment_required'=>true,'message'=>'انتهت الأسئلة المجانية.'],402);$count=max(1,min(50,$count,$remaining));
        $params=[$subjectId,$grade];$chapterSql='';if($chapterIds){$marks=implode(',',array_fill(0,count($chapterIds),'?'));$chapterSql=" AND q.chapter_id IN ($marks)";$params=array_merge($params,$chapterIds);} $live=topic_live_sql('q');
        $sql="SELECT q.id,q.chapter_id,q.type,q.question_text,q.meaning_word,q.correct_answer,q.explanation,q.exam_year,q.exam_round,q.genetics_json,q.calculation_json FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE s.id=? AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' AND s.status=1 AND c.status=1 AND $live $chapterSql ORDER BY q.id DESC LIMIT 1200";
        $stmt=$pdo->prepare($sql);$stmt->execute($params);$rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];$rows=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$rows,true):$rows;
        $qids=array_map(static fn($r)=>(int)$r['id'],$rows);$answered=[];$favorite=[];
        if($qids){$marks=implode(',',array_fill(0,count($qids),'?'));$a=$pdo->prepare("SELECT question_id,MAX(is_correct) last_correct FROM answer_events WHERE user_id=? AND question_id IN ($marks) GROUP BY question_id");$a->execute(array_merge([$uid],$qids));foreach($a->fetchAll(PDO::FETCH_ASSOC)?:[] as $r)$answered[(int)$r['question_id']]=(bool)$r['last_correct'];$f=$pdo->prepare("SELECT question_id FROM question_favorites WHERE user_id=? AND question_id IN ($marks)");$f->execute(array_merge([$uid],$qids));$favorite=array_fill_keys(array_map('intval',$f->fetchAll(PDO::FETCH_COLUMN)),true);}
        $ids=[];foreach($rows as $r){$rt=(string)$r['type'];$id=(int)$r['id'];if(!practice_supported_type($rt))continue;if($type!=='all'&&$rt!==$type)continue;if(!practice_state_ok($id,$state,$answered,$favorite))continue;$ids[]=$id;}
        if(!$ids)json_response(['ok'=>false,'message'=>'لا توجد أسئلة مطابقة لهذه المرشحات.'],404);
        shuffle($ids);$ids=array_slice(array_values(array_unique($ids)),0,$count);if(!$ids)json_response(['ok'=>false,'message'=>'لا توجد أسئلة كافية.'],404);
        $filters=['type'=>$type,'state'=>$state,'chapter_ids'=>$chapterIds,'minutes'=>$minutes];$duration=$minutes>0?max(300,min(10800,$minutes*60)):null;
        try{$stmt=$pdo->prepare('INSERT INTO ai_exam_sessions(user_id,subject_id,title,question_ids_json,filters_json,total_questions,duration_seconds) VALUES(?,?,?,?,?,?,?)');$stmt->execute([$uid,$subjectId,'اختبار مخصص — '.$subject['name'],json_encode($ids),json_encode($filters,JSON_UNESCAPED_UNICODE),count($ids),$duration]);}
        catch(Throwable $e){$stmt=$pdo->prepare('INSERT INTO ai_exam_sessions(user_id,subject_id,title,question_ids_json,total_questions) VALUES(?,?,?,?,?)');$stmt->execute([$uid,$subjectId,'اختبار مخصص — '.$subject['name'],json_encode($ids),count($ids)]);}
        json_response(['ok'=>true,'session_id'=>(int)$pdo->lastInsertId(),'total'=>count($ids)]);
    }
    if($action==='finish'){
        $sid=(int)($data['session_id']??0);$posted=is_array($data['answers']??null)?$data['answers']:[];$pdo->beginTransaction();
        try{
            $s=$pdo->prepare("SELECT * FROM ai_exam_sessions WHERE id=? AND user_id=? AND status='active' FOR UPDATE");$s->execute([$sid,$uid]);$session=$s->fetch(PDO::FETCH_ASSOC);if(!$session){$pdo->rollBack();json_response(['ok'=>false,'message'=>'الاختبار غير موجود أو مكتمل بالفعل.'],409);}
            $ids=json_decode((string)$session['question_ids_json'],true);$ids=is_array($ids)?array_values(array_filter(array_map('intval',$ids),static fn($v)=>$v>0)):[];if(!$ids){$pdo->rollBack();json_response(['ok'=>false,'message'=>'الاختبار فارغ.'],422);}
            $m=implode(',',array_fill(0,count($ids),'?'));$stmt=$pdo->prepare("SELECT q.id,q.chapter_id,q.type,q.question_text,q.correct_answer,q.explanation,q.meaning_word,q.exam_year,q.exam_round,q.genetics_json,q.calculation_json FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE q.id IN ($m) AND q.status=1 AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%'");$stmt->execute(array_merge($ids,[$grade]));$rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];$rows=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$rows,false):$rows;$by=[];foreach($rows as $r)$by[(int)$r['id']]=$r;if(count($by)!==count($ids)){$pdo->rollBack();json_response(['ok'=>false,'message'=>'تغيّر محتوى الاختبار بعد المراجعة. أنشئ اختباراً جديداً؛ لم تُرسل إجاباتك للتصحيح.'],409);}
            $correct=0;$answeredCount=0;$newRecords=0;$newCorrect=0;$grades=[];$blocked=false;$review=[];
            foreach($ids as $id){if(!isset($by[$id]))continue;$r=$by[$id];$given=trim((string)($posted[(string)$id]??$posted[$id]??''));$ok=false;if($given!==''){$gradingType=(string)$r['type'];if($gradingType==='meaning'&&!empty($r['_answer_rows']))$gradingType='mcq';$ok=is_arabic_answer_correct($given,(string)$r['correct_answer'],$gradingType,(string)($r['question_text']??''));$rec=record_answer_event_with_quota($uid,$id,(int)$r['chapter_id'],$ok,$given);if($rec===-1){$blocked=true;break;}if($rec===1){$newRecords++;$newCorrect+=(int)$ok;}$answeredCount++;$correct+=(int)$ok;$grades[(string)$id]=$ok;}
                $review[]=['question_id'=>$id,'question_text'=>public_question_display_text((string)$r['question_text']),'submitted_answer'=>$given,'is_correct'=>$given!==''?$ok:null,'correct_answer'=>(string)$r['correct_answer'],'explanation'=>public_question_explanation($r['explanation']??null,$r['correct_answer']??null)];
            }
            $total=count($ids);$score=$total?round($correct/$total*100,2):0;$pdo->prepare("UPDATE ai_exam_sessions SET answered=?,correct=?,score=?,status='completed',completed_at=NOW() WHERE id=? AND user_id=?")->execute([$answeredCount,$correct,$score,$sid,$uid]);if($newRecords>0){recalc_stats($uid);grant_xp($uid,max(10,$newCorrect*5),'إكمال اختبار مخصص');daily_streak_touch($uid);} $pdo->commit();json_response(['ok'=>true,'correct'=>$correct,'answered'=>$answeredCount,'total'=>$total,'score'=>$score,'grades'=>$grades,'review'=>$review,'quota_blocked'=>$blocked]);
        }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
    }
    json_response(['ok'=>false,'message'=>'عملية غير مدعومة.'],422);
}

if(isset($_GET['history'])){$s=$pdo->prepare("SELECT a.id,a.title,a.total_questions,a.answered,a.correct,a.score,a.created_at,a.completed_at,s.name subject_name FROM ai_exam_sessions a JOIN subjects s ON s.id=a.subject_id WHERE a.user_id=? AND a.status='completed' AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' ORDER BY a.id DESC LIMIT 100");$s->execute([$uid,$grade]);json_response(['ok'=>true,'history'=>$s->fetchAll(PDO::FETCH_ASSOC)?:[]]);}

$sid=(int)($_GET['session_id']??0);
if(!$sid){$s=$pdo->prepare("SELECT a.id,a.title,a.total_questions,a.created_at AS started_at,s.name subject_name FROM ai_exam_sessions a JOIN subjects s ON s.id=a.subject_id WHERE a.user_id=? AND a.status='active' AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' ORDER BY a.id DESC LIMIT 10");$s->execute([$uid,$grade]);json_response(['ok'=>true,'sessions'=>$s->fetchAll(PDO::FETCH_ASSOC)?:[]]);}
$s=$pdo->prepare("SELECT a.*,s.name subject_name FROM ai_exam_sessions a JOIN subjects s ON s.id=a.subject_id WHERE a.id=? AND a.user_id=? AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' LIMIT 1");$s->execute([$sid,$uid,$grade]);$session=$s->fetch(PDO::FETCH_ASSOC);if(!$session)json_response(['ok'=>false,'message'=>'الاختبار غير متاح.'],404);
$ids=json_decode((string)$session['question_ids_json'],true);$ids=is_array($ids)?array_values(array_filter(array_map('intval',$ids),static fn($v)=>$v>0)):[];if(!$ids)json_response(['ok'=>true,'session'=>['id'=>$sid,'status'=>$session['status']],'questions'=>[]]);
$m=implode(',',array_fill(0,count($ids),'?'));$stmt=$pdo->prepare("SELECT q.id,q.type,q.question_text,q.meaning_word,q.correct_answer,q.explanation,q.exam_year,q.exam_round,q.genetics_json,q.calculation_json FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE q.id IN ($m) AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' AND q.status=1");$stmt->execute(array_merge($ids,[$grade]));$rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];$rows=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$rows,false):$rows;$by=[];foreach($rows as $r)$by[(int)$r['id']]=$r;if(count($by)!==count($ids))json_response(['ok'=>false,'message'=>'تغيّر محتوى هذا الاختبار بعد المراجعة. أنشئ اختباراً جديداً.'],409);
$questions=[];foreach($ids as $id)if(isset($by[$id])){$r=$by[$id];$opts=[];foreach((array)($r['_answer_rows']??[]) as $o)$opts[]=['id'=>(int)($o['id']??0),'text'=>(string)($o['answer_text']??'')];$questions[]=['id'=>$id,'type'=>(string)$r['type'],'english_match'=>murshid_v53_public_matching($r['_english_match']??null),'text'=>public_question_display_text((string)$r['question_text']),'meaning_word'=>$r['meaning_word']??null,'options'=>$opts,'exam_year'=>trim((string)($r['exam_year']??'')),'exam_round'=>trim((string)($r['exam_round']??''))];}
json_response(['ok'=>true,'session'=>['id'=>$sid,'title'=>$session['title'],'status'=>$session['status'],'subject'=>$session['subject_name'],'total'=>(int)$session['total_questions'],'duration_seconds'=>(int)($session['duration_seconds']??0)],'questions'=>$session['status']==='active'?$questions:[]]);
