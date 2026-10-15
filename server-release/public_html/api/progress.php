<?php
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
require_once __DIR__ . '/../includes/prize_contest.php';
$u=require_student();
$d=json_decode(file_get_contents('php://input'),true)??[];
if(!verify_csrf($d['csrf']??null))json_response(['ok'=>false,'message'=>'CSRF'],419);
$chapter=(int)($d['chapter_id']??0);
$idx=max(0,(int)($d['question_index']??0));
$incoming=is_array($d['answers']??null)?$d['answers']:[];
$completed=!empty($d['completed'])?1:0;
$pdo=db();
$chapterRow=topic_for_student($pdo,(int)$u['grade_id'],$chapter);
if(!$chapterRow)json_response(['ok'=>false,'message'=>'موضوع غير صالح لهذا الصف'],422);
$chapter=(int)$chapterRow['id'];$topicIds=topic_id_list($chapterRow);
$isEnglishMatchChapter=(in_array((string)$chapterRow['grade_name'],['السادس العلمي','السادس الأدبي'],true) && (string)$chapterRow['subject_name']==='اللغة الإنكليزية' && str_contains((string)$chapterRow['name'],'Match'));

// الحالة السابقة محفوظة بالحساب في الخادم؛ لا نسمح لطلب جديد بتصفيرها.
$old=$pdo->prepare("SELECT current_index,answers_json,completed FROM progress WHERE user_id=? AND chapter_id=? LIMIT 1");
$old->execute([(int)$u['id'],$chapter]);
$oldRow=$old->fetch(PDO::FETCH_ASSOC)?:['current_index'=>0,'answers_json'=>null,'completed'=>0];
$oldAnswers=[];
if(!empty($oldRow['answers_json'])){$tmp=json_decode((string)$oldRow['answers_json'],true);if(is_array($tmp))$oldAnswers=$tmp;}

$live=topic_live_sql('q');
$q=$pdo->prepare("SELECT q.id,q.chapter_id,q.type,q.question_text,q.correct_answer,q.explanation,q.genetics_json,q.calculation_json
 FROM questions q JOIN chapters qc ON qc.id=q.chapter_id JOIN subjects qs ON qs.id=qc.subject_id
 WHERE q.chapter_id IN ($topicIds) AND qs.grade_id=? AND qs.id=? AND qc.status=1 AND qs.status=1 AND $live ORDER BY q.sort_order,q.id");
$q->execute([(int)$u['grade_id'],(int)$chapterRow['subject_id']]);
$rows=$q->fetchAll(PDO::FETCH_ASSOC)?:[];
$rows=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$rows,true):$rows;
$correct=[];$types=[];$gradeTypes=[];$geneticsDefs=[];$calculationDefs=[];
$questionTexts=[];$explanations=[];
foreach($rows as $row){
    $qid=(int)$row['id'];
    $correct[$qid]=trim((string)$row['correct_answer']);
    $types[$qid]=(string)$row['type'];
    $questionTexts[$qid]=(string)($row['question_text']??'');
    $explanations[$qid]=public_question_explanation($row['explanation']??null,$row['correct_answer']??null);
    if(($row['type']??'')==='genetics'&&!empty($row['genetics_json'])){$gj=json_decode((string)$row['genetics_json'],true);if(is_array($gj))$geneticsDefs[$qid]=$gj;}
    if(($row['type']??'')==='calculation'&&!empty($row['calculation_json'])){$cj=json_decode((string)$row['calculation_json'],true);if(is_array($cj))$calculationDefs[$qid]=$cj;}
    $mode=(string)($row['_answer_mode']??'');
    $gradeTypes[$qid]=$mode==='choice' ? (($row['type']??'')==='true_false'?'true_false':'mcq') : (string)($row['type']??'text');
}


$parseEnglishPairs=static function(string $text): array {
    $out=[];
    if(preg_match_all('/(\d+)\s*-\s*([a-zA-Z])/u',$text,$m,PREG_SET_ORDER)){
        foreach($m as $x)$out[(string)((int)$x[1])]=strtolower((string)$x[2]);
    }
    ksort($out,SORT_NATURAL);
    return $out;
};

$normGenetic=static function(string $v): string { $v=preg_replace('/\s+/u','',$v)??$v; $v=str_replace(['x','X','×'], '×',$v); return mb_strtolower(trim($v),'UTF-8'); };
$gradeGenetics=static function(string $given,array $def) use($normGenetic): array {
    $payload=json_decode($given,true); $st=is_array($payload['stages']??null)?$payload['stages']:[];
    $got=0;$total=0;$details=[];
    foreach(($def['stages']??[]) as $stage){
        if(($stage['kind']??'')==='punnett' && is_array($stage['cells']??null)){
            foreach($stage['cells'] as $i=>$cell){$total++;$key=(string)($stage['key']??'punnett').'_'.$i;$gv=(string)($st[$key]??'');$vals=is_array($cell['answers']??null)?$cell['answers']:[(string)($cell['answer']??'')];$ok=false;foreach($vals as $v){if($normGenetic($gv)===$normGenetic((string)$v)){$ok=true;break;}}if($ok)$got++;$details[$key]=['ok'=>$ok,'given'=>$gv,'correct'=>(string)($vals[0]??'')];}
        }else{$total++;$key=(string)($stage['key']??'');$gv=(string)($st[$key]??'');$vals=is_array($stage['answers']??null)?$stage['answers']:[(string)($stage['answer']??'')];$ok=false;foreach($vals as $v){if($normGenetic($gv)===$normGenetic((string)$v)){$ok=true;break;}}if($ok)$got++;$details[$key]=['ok'=>$ok,'given'=>$gv,'correct'=>(string)($vals[0]??'')];}
    }
    $score=$total?(int)round($got/$total*100):0; return ['is_correct'=>$score===100,'status'=>$score===100?'correct':'incorrect','score'=>$score,'message'=>'تم تصحيح '.$got.' من '.$total.' خطوات','details'=>$details];
};
$normalizeCalc=static function(string $v): string {
    $v=strtr(trim($v),['٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9','٫'=>'.','×'=>'x','−'=>'-','–'=>'-','^'=>'^']);
    $v=preg_replace('/\s+/u','',$v)??$v; return mb_strtolower($v,'UTF-8');
};
$calcNumeric=static function(string $v,string $unit=''): ?float {
    return murshid_math_number_with_unit($v,$unit);
};
$gradeCalculation=static function(string $given,array $def) use($normalizeCalc,$calcNumeric): array {
    $payload=json_decode($given,true);$st=is_array($payload['stages']??null)?$payload['stages']:[];$got=0;$total=0;$details=[];
    foreach(($def['stages']??[]) as $stage){
        if(($stage['kind']??'')==='info'||!empty($stage['ungraded'])) continue;
        $total++;$key=(string)($stage['key']??'');$gv=(string)($st[$key]??'');$answers=is_array($stage['answers']??null)?$stage['answers']:[(string)($stage['answer']??'')];$ok=false;
        if(($stage['kind']??'')==='number'){
            $gnum=$calcNumeric($gv,(string)($stage['unit']??''));$tol=(float)($stage['tolerance']??0.001);
            foreach($answers as $ans){$anum=$calcNumeric((string)$ans,(string)($stage['unit']??''));if($gnum!==null&&$anum!==null){$scale=max(1.0,abs($anum));if(abs($gnum-$anum)<=max($tol,1e-8*$scale)){$ok=true;break;}}}
        }else{foreach($answers as $ans){if($normalizeCalc($gv)===$normalizeCalc((string)$ans)){$ok=true;break;}}}
        if($ok)$got++;$details[$key]=['ok'=>$ok,'given'=>$gv,'correct'=>(string)($stage['display_answer']??($answers[0]??''))];
    }
    $score=$total?(int)round($got/$total*100):100;return ['is_correct'=>$score===100,'status'=>$score===100?'correct':'incorrect','score'=>$score,'message'=>'تم تصحيح '.$got.' من '.$total.' مراحل','details'=>$details];
};
$contestBefore=null; // Stable v10: avoid a full leaderboard query before every answer save.
$contestCfg=million_contest_config();
ensure_stats((int)$u['id']);
$balanceQ=$pdo->prepare("SELECT xp FROM user_stats WHERE user_id=? LIMIT 1");
$balanceQ->execute([(int)$u['id']]);
$runningXp=(int)($balanceQ->fetchColumn() ?: 0);
$wrongPenalty=max(0,(int)($contestCfg['wrong_answer_penalty']??2));
$wrongPenaltyThreshold=max(0,(int)($contestCfg['wrong_penalty_threshold']??100));
$submittedValid=0;$correctN=0;$newXp=0;$answerReward=0;$answerPenalty=0;$completionXp=0;$quotaBlocked=false;$grades=[];
foreach($incoming as $qid=>$a){
    if(!is_array($a)||empty($a['submitted']))continue;
    $qid=(int)$qid;
    if(!isset($correct[$qid]))continue;
    $given=trim((string)($a['text']??$a['selected_text']??''));
    if($given==='')continue;
        $isEnglishPair=murshid_v53_matching_definition((string)($questionTexts[$qid]??''))!==null;
    if($isEnglishPair){
        $givenPairs=murshid_v53_parse_pairs($given);$correctPairs=murshid_v53_parse_pairs($correct[$qid]);
        $pairOk=$givenPairs!==null && $givenPairs===$correctPairs;
        $grade=['is_correct'=>$pairOk,'status'=>$pairOk?'correct':'incorrect','score'=>$pairOk?100:0,'message'=>$pairOk?'جميع التوصيلات صحيحة':'توجد توصيلات غير صحيحة'];
    }else{
        if(($types[$qid]??'')==='calculation' && isset($calculationDefs[$qid])){
            $grade=$gradeCalculation($given,$calculationDefs[$qid]);
        }elseif(($types[$qid]??'')==='genetics' && isset($geneticsDefs[$qid])){
            $grade=$gradeGenetics($given,$geneticsDefs[$qid]);
        }elseif(($types[$qid]??'')==='match'){
            $matchOk=is_match_answer_correct($pdo,$qid,$given);
            $grade=['is_correct'=>$matchOk,'status'=>$matchOk?'correct':'incorrect','score'=>$matchOk?100:0,'message'=>$matchOk?'المطابقة صحيحة':'توجد مطابقة غير صحيحة'];
        }else{
            $grade=grade_arabic_answer($given,$correct[$qid],(string)($gradeTypes[$qid]??$types[$qid]??'text'),(string)($questionTexts[$qid]??''));
        }
    }
    $grade['correct_answer']=$correct[$qid]??'';
    $grade['explanation']=$explanations[$qid]??'';
    $is=(bool)($grade['is_correct']??false);
    $record=record_answer_event_with_quota((int)$u['id'],$qid,$chapter,$is,$given);
    if($record===-1){$quotaBlocked=true;continue;}
    if($record===0){
        // A retry must return the immutable first receipt, even if the client
        // submits different text after a lost response. Never show a new grade.
        $receipt=$pdo->prepare("SELECT answer_text,is_correct FROM answer_events WHERE user_id=? AND question_id=? ORDER BY id LIMIT 1");
        $receipt->execute([(int)$u['id'],$qid]);$accepted=$receipt->fetch(PDO::FETCH_ASSOC);
        if(!$accepted)continue;
        $given=(string)$accepted['answer_text'];$is=(bool)$accepted['is_correct'];
        $grade=['is_correct'=>$is,'status'=>$is?'correct':'incorrect','score'=>$is?100:0,
            'message'=>'هذه الإجابة مسجلة سابقاً في حسابك.','correct_answer'=>$correct[$qid]??'',
            'explanation'=>$explanations[$qid]??''];
        $incoming[$qid]['text']=$given;$incoming[$qid]['selected_text']=$given;
        // A new option ID cannot replace the original saved answer.
        unset($incoming[$qid]['selected']);
    }
    $grade['accepted']=true;$grade['already_recorded']=$record===0;
    $grade['accepted_answer']=$given;$grades[$qid]=$grade;
    $submittedValid++;$correctN+=$is?1:0;
    if($record===1){
        $questionType=(string)($types[$qid]??'');
        if($is){
            $correctPoints=in_array($questionType,['calculation','genetics'],true)?10:5;
            $newXp+=$correctPoints;$answerReward+=$correctPoints;$runningXp+=$correctPoints;
        }elseif($wrongPenalty>0 && $runningXp>$wrongPenaltyThreshold){
            $newXp-=$wrongPenalty;$answerPenalty+=$wrongPenalty;$runningXp-=$wrongPenalty;
        }
    }
}
if($newXp!==0)grant_xp((int)$u['id'],$newXp,$newXp>0?'إجابات جديدة':'خصم إجابة خاطئة');

// answer_events هو السجل النهائي للإجابات المقبولة. نعيد بناء حالة submitted منه، لا من المتصفح.
$events=$pdo->prepare("SELECT ae.question_id,ae.answer_text,ae.is_correct FROM answer_events ae JOIN questions q ON q.id=ae.question_id JOIN chapters qc ON qc.id=q.chapter_id JOIN subjects qs ON qs.id=qc.subject_id WHERE ae.user_id=? AND q.chapter_id IN ($topicIds) AND qs.grade_id=? AND qs.id=? AND qc.status=1 AND qs.status=1 AND $live ORDER BY ae.id");
$events->execute([(int)$u['id'],(int)$u['grade_id'],(int)$chapterRow['subject_id']]);
$eventMap=[];
foreach($events->fetchAll(PDO::FETCH_ASSOC) as $ev){$eid=(int)$ev['question_id'];if(isset($correct[$eid]))$eventMap[$eid]=$ev;}

$safeAnswers=$oldAnswers;
foreach($incoming as $qid=>$a){
    $qid=(int)$qid;
    if(!isset($correct[$qid])||!is_array($a))continue;
    if(isset($eventMap[$qid])){
        $stored=(string)($eventMap[$qid]['answer_text']??'');
        $base=is_array($safeAnswers[$qid]??null)?$safeAnswers[$qid]:[];
        // نحافظ على selected إن كان اختياراً، لكن النص والحالة تأتيان من قاعدة البيانات.
        if(isset($a['selected']))$base['selected']=$a['selected'];
        $base['text']=$stored;$base['selected_text']=$stored;$base['submitted']=true;
        if(isset($grades[$qid])){$base['grade_status']=$grades[$qid]['status'];$base['grade_score']=$grades[$qid]['score'];$base['grade_message']=$grades[$qid]['message'];$base['is_correct']=(bool)$grades[$qid]['is_correct'];}
        $safeAnswers[$qid]=$base;
    }else{
        // مسودة غير مقبولة بعد (مثلاً السؤال 31) لا تتحول إلى إجابة مكتملة.
        $draft=$a;$draft['submitted']=false;$safeAnswers[$qid]=$draft;
    }
}
foreach($eventMap as $qid=>$ev){
    $stored=(string)($ev['answer_text']??'');
    $base=is_array($safeAnswers[$qid]??null)?$safeAnswers[$qid]:[];
    $base['text']=$stored;$base['selected_text']=$stored;$base['submitted']=true;
    $safeAnswers[$qid]=$base;
}

$oldIndex=max(0,(int)($oldRow['current_index']??0));
// التقدم أحادي الاتجاه على الحساب: لا يمكن إعادة current_index إلى الصفر من المتصفح.
$safeIndex=min(count($correct),$quotaBlocked?$oldIndex:max($oldIndex,$idx));
// Completion and its reward require all published questions, not a client flag.
$safeCompleted=(!$quotaBlocked && count($correct)>0 && count($eventMap)===count($correct))?1:0;
$answerJson=json_encode($safeAnswers,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
$up=$pdo->prepare("INSERT INTO progress(user_id,chapter_id,current_index,answers_json,completed,updated_at) VALUES(?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE current_index=VALUES(current_index),answers_json=VALUES(answers_json),completed=VALUES(completed),updated_at=NOW()");
$up->execute([(int)$u['id'],$chapter,$safeIndex,$answerJson,$safeCompleted]);

ensure_stats((int)$u['id']);
if($safeCompleted&&!$quotaBlocked){
    $q=$pdo->prepare("SELECT COUNT(*) FROM exam_attempts WHERE user_id=? AND chapter_id=? AND completed_at IS NOT NULL AND started_at>=DATE_SUB(NOW(),INTERVAL 1 DAY)");$q->execute([(int)$u['id'],$chapter]);
    if(!(int)$q->fetchColumn()){
        $total=count($eventMap);$correctTotal=0;foreach($eventMap as $ev)$correctTotal+=(int)($ev['is_correct']??0);
        $score=$total?($correctTotal/$total*100):0;
        $pdo->prepare("INSERT INTO exam_attempts(user_id,chapter_id,started_at,completed_at,total_questions,answered,correct,score) VALUES(?,?,NOW(),NOW(),?,?,?,?)")->execute([(int)$u['id'],$chapter,$total,$total,$correctTotal,$score]);
        grant_xp((int)$u['id'],50,'إكمال اختبار');$completionXp=50;notify_user((int)$u['id'],'🎉 اكتمل الاختبار','تم حفظ إكمال الموضوع في حسابك وحصلت على 50 نقطة إضافية.');
    }
}
recalc_stats((int)$u['id']);
$subscribed=is_subscribed((int)$u['id']);$used=free_answers_used((int)$u['id']);$remaining=$subscribed?null:max(0,FREE_QUESTION_LIMIT-$used);
if($quotaBlocked)json_response(['ok'=>false,'payment_required'=>true,'message'=>'أكملت '.FREE_QUESTION_LIMIT.' سؤالاً مجانياً. تقدمك محفوظ في حسابك ويلزم الاشتراك للمتابعة.','free_used'=>$used,'free_remaining'=>0,'subscribed'=>false],402);
$contestAfter=['rank'=>null,'xp'=>0,'correct'=>0,'points_to_next'=>0,'next_rank'=>null];
try{
    $cx=$pdo->prepare("SELECT COALESCE(SUM(amount),0) FROM xp_events WHERE user_id=? AND created_at BETWEEN ? AND ?");
    $cx->execute([(int)$u['id'],$contestCfg['start_at'],$contestCfg['end_at']]);
    $contestAfter['xp']=(int)$cx->fetchColumn();
    $cc=$pdo->prepare("SELECT COALESCE(SUM(is_correct),0) FROM answer_events WHERE user_id=? AND created_at BETWEEN ? AND ?");
    $cc->execute([(int)$u['id'],$contestCfg['start_at'],$contestCfg['end_at']]);
    $contestAfter['correct']=(int)$cc->fetchColumn();
}catch(Throwable $e){error_log('contest lightweight progress snapshot: '.$e->getMessage());}
$contestGain=$newXp+$completionXp;
$contestUpdate=[
    'active'=>million_contest_is_active(),
    'xp_gain'=>$contestGain,
    'answer_xp_gain'=>$newXp,
    'answer_reward'=>$answerReward,
    'answer_penalty'=>$answerPenalty,
    'penalty_threshold'=>$wrongPenaltyThreshold,
    'completion_xp_gain'=>$completionXp,
    'rank_before'=>$contestBefore['rank']??null,
    'rank_after'=>$contestAfter['rank']??null,
    'contest_xp'=>$contestAfter['xp']??0,
    'correct'=>$contestAfter['correct']??0,
    'points_to_next'=>$contestAfter['points_to_next']??0,
    'next_rank'=>$contestAfter['next_rank']??null,
];
json_response(['ok'=>true,'free_used'=>$used,'free_remaining'=>$remaining,'subscribed'=>$subscribed,'current_index'=>$safeIndex,'completed'=>(bool)$safeCompleted,'grades'=>$grades,'contest'=>$contestUpdate]);
