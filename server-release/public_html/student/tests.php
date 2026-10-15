<?php
declare(strict_types=1);
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
$u = require_student();
$subscribed = is_subscribed((int)$u['id']);
$freeRemaining = $subscribed ? PHP_INT_MAX : free_answers_remaining((int)$u['id']);
$pdo = db();

function ai_exam_subject(PDO $pdo, int $subjectId, int $gradeId): ?array {
    $q=$pdo->prepare("SELECT id,name FROM subjects WHERE id=? AND grade_id=? AND status=1 AND name NOT LIKE '%فرن%' AND LOWER(name) NOT LIKE '%french%' AND LOWER(name) NOT LIKE '%franc%' LIMIT 1");
    $q->execute([$subjectId,$gradeId]); return $q->fetch(PDO::FETCH_ASSOC) ?: null;
}
function ai_pick_questions(PDO $pdo,int $userId,int $gradeId,int $subjectId,int $limit):array{
    $limit=max(1,min(30,$limit));
    $live=topic_live_sql('q');
    $base="SELECT q.id FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE s.id=? AND s.grade_id=? AND s.status=1 AND c.status=1 AND $live AND q.type NOT IN ('genetics','calculation')";
    $st=$pdo->prepare($base." AND NOT EXISTS (SELECT 1 FROM answer_events ae WHERE ae.user_id=? AND ae.question_id=q.id AND ae.created_at>=DATE_SUB(NOW(),INTERVAL 14 DAY)) ORDER BY q.id DESC LIMIT ".max($limit*5,40));
    $st->execute([$subjectId,$gradeId,$userId]); $ids=array_map('intval',array_column($st->fetchAll(PDO::FETCH_ASSOC),'id'));
    if(count($ids)<$limit){$need=$limit-count($ids);$params=[$subjectId,$gradeId];$where='';if($ids){$marks=implode(',',array_fill(0,count($ids),'?'));$where=" AND q.id NOT IN($marks)";$params=array_merge($params,$ids);} $st=$pdo->prepare($base.$where." ORDER BY q.id DESC LIMIT ".max($need*5,30));$st->execute($params);foreach($st->fetchAll(PDO::FETCH_ASSOC) as $r)$ids[]=(int)$r['id'];}
    shuffle($ids);return array_slice(array_values(array_unique($ids)),0,$limit);
}

if($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['action']??'')==='generate'){
    if(!verify_csrf($_POST['csrf']??null)){http_response_code(419);exit('طلب غير صالح.');}
    $subjectId=(int)($_POST['subject_id']??0);$count=(int)($_POST['question_count']??10);if(!$subscribed)$count=min($count,max(0,$freeRemaining));
    $subject=ai_exam_subject($pdo,$subjectId,(int)$u['grade_id']);
    if(!$subject){$error='المادة غير متاحة.';}
    else{
        $ids=ai_pick_questions($pdo,(int)$u['id'],(int)$u['grade_id'],$subjectId,$count);
        if(!$ids)$error=$freeRemaining<=0?'أكملت '.FREE_QUESTION_LIMIT.' سؤالاً مجانياً. فعّل الاشتراك لإنشاء اختبار جديد.':'لا توجد أسئلة كافية في هذه المادة.';else{$title='اختبار — '.$subject['name'];$q=$pdo->prepare("INSERT INTO ai_exam_sessions(user_id,subject_id,title,question_ids_json,total_questions) VALUES(?,?,?,?,?)");$q->execute([(int)$u['id'],$subjectId,$title,json_encode($ids),count($ids)]);$sid=(int)$pdo->lastInsertId();audit_log('إنشاء اختبار','ai_exam',(string)$sid);redirect('/student/tests.php?session_id='.$sid);}
    }
}

if($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['action']??'')==='finish'){
    if(!verify_csrf($_POST['csrf']??null)){http_response_code(419);exit('طلب غير صالح.');}
    $sid=(int)($_POST['session_id']??0);$st=$pdo->prepare("SELECT a.* FROM ai_exam_sessions a JOIN subjects s ON s.id=a.subject_id WHERE a.id=? AND a.user_id=? AND a.status='active' AND s.grade_id=? AND s.status=1 AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' LIMIT 1");$st->execute([$sid,(int)$u['id'],(int)$u['grade_id']]);$session=$st->fetch(PDO::FETCH_ASSOC);
    if(!$session)$error='الاختبار غير موجود أو تم إنهاؤه مسبقاً.';else{
        $posted=json_decode((string)($_POST['answers_json']??'{}'),true);$posted=is_array($posted)?$posted:[];$rows=[];
        $ids=json_decode((string)$session['question_ids_json'],true);$ids=is_array($ids)?array_values(array_filter(array_map('intval',$ids))):[];
        if($ids){$m=implode(',',array_fill(0,count($ids),'?'));$q=$pdo->prepare("SELECT q.id,q.chapter_id,q.type,q.question_text,q.correct_answer,(SELECT COUNT(*) FROM answers a WHERE a.question_id=q.id) option_count FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE q.id IN($m) AND q.status=1 AND c.status=1 AND s.status=1 AND s.id=? AND s.grade_id=?");$q->execute(array_merge($ids,[(int)$session['subject_id'],(int)$u['grade_id']]));$rows=$q->fetchAll(PDO::FETCH_ASSOC);}
        else{$q=$pdo->prepare("SELECT id,chapter_id,correct_answer FROM ai_generated_questions WHERE session_id=? ORDER BY sort_order,id");$q->execute([$sid]);$rows=$q->fetchAll(PDO::FETCH_ASSOC);}
        $correct=0;$answered=0;
        foreach($rows as $r){$qid=(int)$r['id'];$answer=trim((string)($posted[$qid]??''));if($answer==='')continue;$ok=(($r['type']??'')==='match') ? is_match_answer_correct($pdo,$qid,$answer) : is_arabic_answer_correct($answer,trim((string)$r['correct_answer']),((($r['type']??'')==='meaning'&&(int)($r['option_count']??0)>0)?'mcq':(string)($r['type']??'text')),(string)($r['question_text']??''));$chapterId=(int)($r['chapter_id']??0);if($chapterId<=0)continue;$record=record_answer_event_with_quota((int)$u['id'],$qid,$chapterId,$ok,$answer);if($record===-1){$error='أكملت '.FREE_QUESTION_LIMIT.' سؤالاً مجانياً. يلزم الاشتراك لمواصلة الإجابة.';break;}$answered++;if($ok)$correct++;}
        $total=count($rows);$score=$total?round($correct/$total*100,2):0;$up=$pdo->prepare("UPDATE ai_exam_sessions SET answered=?,correct=?,score=?,status='completed',completed_at=NOW() WHERE id=? AND user_id=?");$up->execute([$answered,$correct,$score,$sid,(int)$u['id']]);grant_xp((int)$u['id'],max(10,(int)round($correct*5)),'إكمال اختبار');daily_streak_touch((int)$u['id']);audit_log('إنهاء اختبار','ai_exam',(string)$sid);$result=['correct'=>$correct,'answered'=>$answered,'total'=>$total,'score'=>$score];
    }
}

$session=null;$questions=[];
if(isset($_GET['session_id'])){$sid=(int)$_GET['session_id'];$q=$pdo->prepare("SELECT a.*,s.name subject_name FROM ai_exam_sessions a JOIN subjects s ON s.id=a.subject_id WHERE a.id=? AND a.user_id=? AND s.grade_id=? AND s.status=1 AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' LIMIT 1");$q->execute([$sid,(int)$u['id'],(int)$u['grade_id']]);$session=$q->fetch(PDO::FETCH_ASSOC)?:null;if($session && $session['status']==='active'){
    $ids=json_decode((string)$session['question_ids_json'],true);$ids=is_array($ids)?array_map('intval',$ids):[];
    if($ids){
        $m=implode(',',array_fill(0,count($ids),'?'));
        $st=$pdo->prepare("SELECT q.id,q.type,q.question_text,q.meaning_word,q.correct_answer,q.explanation,q.verification_status,q.exam_year,q.exam_round,c.id chapter_id,c.name chapter_name,s.name subject_name FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE q.id IN($m) AND q.status=1 AND c.status=1 AND s.status=1 AND s.id=? AND s.grade_id=?");
        $st->execute(array_merge($ids,[(int)$session['subject_id'],(int)$u['grade_id']]));
        $by=[];foreach($st->fetchAll(PDO::FETCH_ASSOC) as $r)$by[(int)$r['id']]=$r;
        $answersBy=[];$matchBy=[];
        $a=$pdo->prepare("SELECT question_id,answer_text FROM answers WHERE question_id IN($m) ORDER BY question_id,sort_order,id");
        $a->execute($ids);foreach($a->fetchAll(PDO::FETCH_ASSOC)?:[] as $ar)$answersBy[(int)$ar['question_id']][]=$ar['answer_text'];
        $mi=$pdo->prepare("SELECT question_id,id,mad_word,correct_type,reason_text FROM match_items WHERE question_id IN($m) ORDER BY question_id,sort_order,id");
        $mi->execute($ids);foreach($mi->fetchAll(PDO::FETCH_ASSOC)?:[] as $mr)$matchBy[(int)$mr['question_id']][]=['id'=>(int)$mr['id'],'mad_word'=>$mr['mad_word'],'correct_type'=>$mr['correct_type'],'reason_text'=>$mr['reason_text']];
        foreach($ids as $id)if(isset($by[$id])){$r=$by[$id];$r['ministerial_badge']=public_question_ministerial_label($r);$r['answers']=$answersBy[$id]??[];$r['match_items']=$matchBy[$id]??[];$questions[]=$r;}
    }
    else{$st=$pdo->prepare("SELECT id,type,question_text,correct_answer,explanation,options_json FROM ai_generated_questions WHERE session_id=? ORDER BY sort_order,id");$st->execute([$sid]);foreach($st->fetchAll(PDO::FETCH_ASSOC) as $r){$r['ministerial_badge']=['label'=>'وزاري','kind'=>'official'];$r['answers']=json_decode((string)$r['options_json'],true)?:[];$questions[]=$r;}}
}}
$subjects=[];try{$st=$pdo->prepare("SELECT id,name FROM subjects WHERE grade_id=? AND status=1 AND name NOT LIKE '%فرن%' AND LOWER(name) NOT LIKE '%french%' AND LOWER(name) NOT LIKE '%franc%' ORDER BY sort_order,id");$st->execute([(int)$u['grade_id']]);$subjects=$st->fetchAll(PDO::FETCH_ASSOC);}catch(Throwable $e){error_log('ai subjects: '.$e->getMessage());}
?>
<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>الاختبار</title>
<script src="<?=BASE_URL?>/assets/js/theme-init.js?v=20260928-v56"></script><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/platform.css?v=20260929-v584contrast"><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/brand-v57.css?v=20260929-v5811responsive">
<link rel="icon" type="image/png" sizes="512x512" href="<?= BASE_URL ?>/assets/img/site-icon-512.png?v=20260928-v56"><link rel="apple-touch-icon" sizes="180x180" href="<?= BASE_URL ?>/assets/img/apple-touch-icon.png?v=20260928-v56">
</head><body class="page cinema-study-skin platform-student" data-es-role="student" data-es-base=".." data-es-name="<?=e($u['full_name'])?>">
<?php platform_bar('student'); ?><main class="ai-wrap">
<?php if(isset($result)): ?><section class="ai-card"><div class="hero-symbol"><?=ui_icon('target')?></div><h1 style="text-align:center">تم إنهاء الاختبار</h1><div class="score"><?=e((string)$result['score'])?>%</div><p style="text-align:center">الصحيح: <b><?=e((string)$result['correct'])?></b> من <?=e((string)$result['total'])?> — تمت الإجابة عن <?=e((string)$result['answered'])?>.</p><div class="actions" style="justify-content:center"><a class="btn" href="tests.php">اختبار جديد</a><a class="btn gold" href="<?=BASE_URL?>/">الرئيسية</a></div></section>
<?php elseif($session && $session['status']==='active' && $questions): ?><section class="ai-card"><h1><?=e($session['title'])?></h1><p class="muted">الاختبار محفوظ في الحساب.</p><?php if(!$subscribed):?><div class="quota-chip">المتبقي مجاناً: <?=e((string)max(0,$freeRemaining))?> من <?=FREE_QUESTION_LIMIT?></div><?php endif;?><form method="post" id="examForm"><input type="hidden" name="action" value="finish"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="session_id" value="<?=e((string)$session['id'])?>"><input type="hidden" name="answers_json" id="answersJson"><div id="questions"><?php foreach($questions as $i=>$q): ?><div class="q ai-question-card"><?php if(($session['subject_name']??'')==='الأدب والنصوص'):?><div class="question-source-author">سعد خلف الحمراني</div><?php endif;?><?php $mb=['label'=>'وزاري','kind'=>'official'];?><div class="question-ministerial-badge <?=($mb['kind']??'practice')==='practice'?'practice':''?>">وزاري</div><?php if(!empty($q['subject_name'])):?><div class="question-exam-source"><span><?=e((string)$q['subject_name'])?></span><?php if(!empty($q['chapter_name'])):?><span><?=e(public_topic_name((string)$q['subject_name'],(string)$q['chapter_name']))?></span><?php endif;?></div><?php endif;?><h3 class="ai-question-text"><?=($i+1)?>. <?=e(public_question_display_text($q['question_text']??''))?></h3><?php if(($q['type']??'mcq')==='match'): ?><div class="tajweed-match-list"><?php foreach(($q['match_items']??[]) as $mi): ?><div class="tajweed-match-row"><div class="tajweed-word-box"><span>الكلمة</span><strong><?=e($mi['mad_word'])?></strong></div><label class="tajweed-field"><span>نوع المد</span><select class="ai-match-type" data-q="<?=e((string)$q['id'])?>" data-item="<?=e((string)$mi['id'])?>"><option value="">اختر نوع المد</option><?php foreach(MAD_TYPES() as $mt):?><option value="<?=e($mt)?>"><?=e($mt)?></option><?php endforeach;?></select></label><label class="tajweed-field tajweed-reason"><span>اكتب السبب</span><input class="ai-match-reason" data-q="<?=e((string)$q['id'])?>" data-item="<?=e((string)$mi['id'])?>" placeholder="اذكر سبب نوع المد"></label></div><?php endforeach;?></div><?php elseif(in_array(($q['type']??'mcq'),['mcq','meaning','true_false'],true) && !empty($q['answers'])): foreach($q['answers'] as $ans): ?><button type="button" class="opt" data-q="<?=e((string)$q['id'])?>" data-a="<?=e((string)$ans)?>"><?=e((string)$ans)?></button><?php endforeach; else: ?><input class="ai-text-answer" data-q="<?=e((string)$q['id'])?>" placeholder="اكتب إجابتك..."><?php endif; ?></div><?php endforeach; ?></div><div class="actions"><button class="btn" type="submit">إنهاء وتصحيح الاختبار</button><a class="btn gold" href="<?=BASE_URL?>/">العودة</a></div></form></section>
<?php else: ?><section class="ai-card"><div class="hero-symbol"><?=ui_icon('target')?></div><h1>الاختبار</h1><?php if(!$subscribed):?><div class="quota-chip">استخدمت <?=e((string)free_answers_used((int)$u['id']))?> / <?=FREE_QUESTION_LIMIT?> سؤالاً مجانياً</div><?php endif;?><p class="muted">اختر المادة وعدد الأسئلة، وسيجهز لك النظام اختباراً من بنك أسئلة المادة، مع تنويع الأسئلة وعدم تغييرها بعد بدء الجلسة.</p><?php if(isset($error)): ?><div class="error">⚠️ <?=e($error)?></div><?php endif; ?><form method="post"><input type="hidden" name="action" value="generate"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><div class="grid"><label class="field">المادة<select name="subject_id" class="el-pill" required><?php foreach($subjects as $s): ?><option value="<?=e((string)$s['id'])?>"><?=e($s['name'])?></option><?php endforeach; ?></select></label><label class="field">عدد الأسئلة<select name="question_count" class="el-pill"><?php $counts=[5,10,15,20];if(!$subscribed&&$freeRemaining>0&&$freeRemaining<5)$counts=[$freeRemaining];foreach($counts as $n):if($subscribed||$freeRemaining >= $n):?><option <?=($n===10?'selected':'')?>><?=$n?></option><?php endif;endforeach;?></select></label></div><div class="actions"><?php if(!$subscribed && $freeRemaining<=0):?><a class="btn" href="../subscription.php">🔒 الاشتراك للمتابعة</a><?php else:?><button class="btn" type="submit">إنشاء الاختبار</button><?php endif;?><a class="btn gold" href="<?=BASE_URL?>/">الرئيسية</a></div></form></section><?php endif; ?></main>
<script>
const answers={},matchAnswers={};
document.querySelectorAll('.opt').forEach(b=>b.addEventListener('click',()=>{const q=b.dataset.q;answers[q]=b.dataset.a;document.querySelectorAll('.opt[data-q="'+CSS.escape(q)+'"]').forEach(x=>x.classList.remove('sel'));b.classList.add('sel')}));
document.querySelectorAll('.ai-text-answer').forEach(x=>x.addEventListener('input',()=>answers[x.dataset.q]=x.value));
function syncAiMatch(el,key){const q=el.dataset.q,item=el.dataset.item;matchAnswers[q]=matchAnswers[q]||{};matchAnswers[q][item]=matchAnswers[q][item]||{type:'',reason:''};matchAnswers[q][item][key]=el.value;answers[q]=JSON.stringify({items:matchAnswers[q]});}
document.querySelectorAll('.ai-match-type').forEach(x=>x.addEventListener('change',()=>syncAiMatch(x,'type')));
document.querySelectorAll('.ai-match-reason').forEach(x=>x.addEventListener('input',()=>syncAiMatch(x,'reason')));
const f=document.getElementById('examForm');if(f)f.addEventListener('submit',e=>{let missing=false;document.querySelectorAll('.ai-match-type').forEach(x=>{if(!x.value)missing=true});document.querySelectorAll('.ai-match-reason').forEach(x=>{if(!x.value.trim())missing=true});if(missing){e.preventDefault();alert('اختر نوع المد واكتب السبب لكل كلمة.');return;}document.getElementById('answersJson').value=JSON.stringify(answers)});
</script><script defer src="<?= BASE_URL ?>/assets/js/app.js?v=20260929-v586stories"></script>
</body></html>
