<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
$u=mobile_student();$pdo=db();$live=topic_live_sql('q');
$sql="SELECT c.id chapter_id,c.name chapter_name,s.name subject_name,s.id subject_id,COUNT(*) total,SUM(a.is_correct=0) wrong,ROUND(100*SUM(a.is_correct)/COUNT(*),0) rate
 FROM answer_events a JOIN questions q ON q.id=a.question_id JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
 WHERE a.user_id=? AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' AND c.status=1 AND s.status=1 AND $live GROUP BY c.id,c.name,s.name,s.id ORDER BY rate ASC,wrong DESC LIMIT 15";
$q=$pdo->prepare($sql);$q->execute([(int)$u['id'],(int)$u['grade_id']]);$weak=$q->fetchAll(PDO::FETCH_ASSOC);
$q=$pdo->prepare("SELECT a.question_id,q.type,q.question_text,q.correct_answer,q.explanation,c.id chapter_id,c.name chapter_name,s.id subject_id,s.name subject_name,MAX(a.created_at) last_seen
 FROM answer_events a JOIN questions q ON q.id=a.question_id JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
 WHERE a.user_id=? AND a.is_correct=0 AND s.grade_id=? AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%' AND c.status=1 AND s.status=1 AND $live GROUP BY a.question_id,q.type,q.question_text,q.correct_answer,q.explanation,c.id,c.name,s.id,s.name ORDER BY last_seen DESC LIMIT 30");
$q->execute([(int)$u['id'],(int)$u['grade_id']]);$mistakes=[];
foreach($q->fetchAll(PDO::FETCH_ASSOC) as $m){
    if($m['type']==='match')$m['correct_answer']=match_model_answer($pdo,(int)$m['question_id']);
    $m['english_match']=murshid_v53_public_matching(murshid_v53_matching_definition((string)$m['question_text']));
    $m['question_text']=public_question_display_text($m['question_text']??'');$m['source_label']='وزاري';
    $m['explanation']=public_question_explanation($m['explanation']??null,$m['correct_answer']??null);$mistakes[]=$m;
}
json_response(['ok'=>true,'weak'=>$weak,'mistakes'=>$mistakes]);
