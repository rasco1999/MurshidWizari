<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
require_once __DIR__.'/../includes/daily_challenge_v48.php';
$u=mobile_student();$pdo=db();murshid_daily_v48_schema($pdo);
$q=murshid_daily_v48_question($pdo,(int)$u['grade_id']);
$prior=murshid_daily_v48_attempt($pdo,(int)$u['id']);
if(!$q && !$prior)json_response(['ok'=>true,'available'=>false,'done'=>false,'questions'=>[],'reward'=>0]);
if($_SERVER['REQUEST_METHOD']==='POST'){
    $data=json_decode((string)file_get_contents('php://input'),true);
    if(!is_array($data)||!verify_csrf($data['csrf']??null))json_response(['ok'=>false,'message'=>'انتهت صلاحية الطلب.'],419);
    if($prior)json_response(['ok'=>false,'message'=>'أجبت عن تحدي اليوم مسبقاً.'],409);
    if(!$q)json_response(['ok'=>false,'message'=>'لا يوجد سؤال متاح حالياً.'],404);
    $ans=$data['answers']??[];$value=is_array($ans)?(string)($ans[(string)$q['id']]??$ans[(int)$q['id']]??''):'';
    $result=murshid_daily_v48_submit($pdo,$u,$q,$value);
    if($result['ok']){$result['given_answer']=trim($value);$result['explanation']=(string)($q['explanation']??'');}
    json_response($result,$result['ok']?200:422);
}
$question=$q?['id'=>(int)$q['id'],'type'=>(string)$q['type'],'question_text'=>public_question_display_text((string)$q['question_text']),'source_label'=>'وزاري',
 'subject_name'=>(string)$q['subject_name'],'options'=>$q['options']]:null;
$answerDetails=$prior?murshid_daily_v48_answer_details($pdo,(int)$prior['question_id'],(int)$u['grade_id']):null;
json_response(['ok'=>true,'available'=>true,'done'=>$prior!==null,'score'=>(int)($prior['is_correct']??0),
 'total'=>$prior?1:0,'reward'=>0,'questions'=>$prior?[]:[$question],
 'given_answer'=>$prior?(string)$prior['given_answer']:null,
 'correct_answer'=>$answerDetails?(string)$answerDetails['correct_answer']:null,
 'explanation'=>$answerDetails?(string)($answerDetails['explanation']??''):null,
 'next_reset_at'=>$prior?(new DateTimeImmutable((string)$prior['answered_at'],new DateTimeZone('Asia/Baghdad')))->modify('+24 hours')->format(DATE_ATOM):null]);
