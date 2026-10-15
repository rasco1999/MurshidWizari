<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
require_once __DIR__.'/../includes/sixth_english_match.php';
require_once __DIR__.'/../includes/platform_tools.php';
require_once __DIR__.'/../includes/hint_free.php';

$u=mobile_student();
$pdo=db();
platform_tools_schema($pdo);
$ch=topic_for_student($pdo,(int)$u['grade_id'],(int)($_GET['chapter_id']??0));
if($ch && preg_match('/فرن|french|franc/iu',(string)($ch['subject_name']??'')))json_response(['ok'=>false,'message'=>'المادة غير متاحة.'],404);
if(!$ch)json_response(['ok'=>false,'message'=>'الموضوع غير متاح.'],404);

$ids=topic_id_list($ch);
$live=topic_live_sql('q');
$stmt=$pdo->query("SELECT q.id,q.type,q.question_text,q.meaning_word,q.correct_answer,q.explanation,q.genetics_json,q.calculation_json,q.exam_year,q.exam_round,q.difficulty
    FROM questions q WHERE q.chapter_id IN ($ids) AND $live ORDER BY q.sort_order,q.id");
$allRows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];
$allRows=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$allRows,true):$allRows;

// ترتيب ثابت حسب الطالب لبعض المواد، بعد استبعاد الأسئلة غير الصالحة بنيوياً.
if(in_array((string)$ch['subject_name'],['اللغة الإنكليزية','الأحياء'],true)){
    usort($allRows,static fn($a,$b)=>strcmp(
        hash('sha256',$u['id'].'|'.$ch['id'].'|'.$a['id']),
        hash('sha256',$u['id'].'|'.$ch['id'].'|'.$b['id'])
    ));
}

$validIds=array_values(array_filter(array_map(static fn($r)=>(int)($r['id']??0),$allRows)));
$validSet=array_fill_keys($validIds,true);
$answered=[];
if($validIds){
    $event=$pdo->prepare("SELECT ae.question_id,ae.answer_text,ae.is_correct
        FROM answer_events ae JOIN questions q ON q.id=ae.question_id
        WHERE ae.user_id=? AND q.chapter_id IN ($ids) ORDER BY ae.id");
    $event->execute([(int)$u['id']]);
    foreach($event->fetchAll(PDO::FETCH_ASSOC)?:[] as $e){
        $qid=(int)$e['question_id'];
        if(isset($validSet[$qid]))$answered[$qid]=['text'=>$e['answer_text'],'correct'=>(bool)$e['is_correct']];
    }
}

$favoriteIds=[];
if($validIds){
    $marks=implode(',',array_fill(0,count($validIds),'?'));
    $f=$pdo->prepare("SELECT question_id FROM question_favorites WHERE user_id=? AND question_id IN ($marks)");
    $f->execute(array_merge([(int)$u['id']],$validIds));
    $favoriteIds=array_fill_keys(array_map('intval',$f->fetchAll(PDO::FETCH_COLUMN)),true);
}

// مرشحات الطالب — جميعها اختيارية ولا تغيّر أصل السؤال أو نتيجة التصحيح.
$typeFilter=trim((string)($_GET['type']??'all'));
$yearFilter=trim((string)($_GET['year']??'all'));
$roundFilter=trim((string)($_GET['round']??'all'));
$difficultyFilter=trim((string)($_GET['difficulty']??'all'));
$stateFilter=trim((string)($_GET['state']??'all'));
$shuffle=(int)($_GET['shuffle']??0)===1;
$limit=max(0,min(100,(int)($_GET['limit']??0)));
$allowedTypes=['all','mcq','true_false','text','fill','meaning','match','genetics','calculation'];
$allowedStates=['all','unanswered','wrong','correct','favorite'];
$allowedDifficulties=['all','easy','medium','hard'];
if(!in_array($typeFilter,$allowedTypes,true))$typeFilter='all';
if(!in_array($stateFilter,$allowedStates,true))$stateFilter='all';
if(!in_array($difficultyFilter,$allowedDifficulties,true))$difficultyFilter='all';

$filterMeta=['types'=>[],'years'=>[],'rounds'=>[],'difficulties'=>[]];
foreach($allRows as $r){
    $t=(string)($r['type']??'text');if($t!==''&&!in_array($t,$filterMeta['types'],true))$filterMeta['types'][]=$t;
    $y=trim((string)($r['exam_year']??''));if($y!==''&&!in_array($y,$filterMeta['years'],true))$filterMeta['years'][]=$y;
    $ro=trim((string)($r['exam_round']??''));if($ro!==''&&!in_array($ro,$filterMeta['rounds'],true))$filterMeta['rounds'][]=$ro;
    $di=trim((string)($r['difficulty']??''));if($di!==''&&!in_array($di,$filterMeta['difficulties'],true))$filterMeta['difficulties'][]=$di;
}
sort($filterMeta['years']);sort($filterMeta['rounds']);

$rows=array_values(array_filter($allRows,static function(array $r) use($typeFilter,$yearFilter,$roundFilter,$difficultyFilter,$stateFilter,$answered,$favoriteIds): bool {
    $id=(int)($r['id']??0);
    if($typeFilter!=='all'&&(string)($r['type']??'')!==$typeFilter)return false;
    if($yearFilter!=='all'&&trim((string)($r['exam_year']??''))!==$yearFilter)return false;
    if($roundFilter!=='all'&&trim((string)($r['exam_round']??''))!==$roundFilter)return false;
    if($difficultyFilter!=='all'&&trim((string)($r['difficulty']??''))!==$difficultyFilter)return false;
    if($stateFilter==='unanswered'&&isset($answered[$id]))return false;
    if($stateFilter==='wrong'&&(!isset($answered[$id])||!empty($answered[$id]['correct'])))return false;
    if($stateFilter==='correct'&&(!isset($answered[$id])||empty($answered[$id]['correct'])))return false;
    if($stateFilter==='favorite'&&!isset($favoriteIds[$id]))return false;
    return true;
}));

if($shuffle){
    usort($rows,static fn($a,$b)=>strcmp(
        hash('sha256','filter|'.$u['id'].'|'.$ch['id'].'|'.date('Y-m-d').'|'.$a['id']),
        hash('sha256','filter|'.$u['id'].'|'.$ch['id'].'|'.date('Y-m-d').'|'.$b['id'])
    ));
}
if($limit>0)$rows=array_slice($rows,0,$limit);

$subscribed=is_subscribed((int)$u['id']);
$used=free_answers_used((int)$u['id']);
$remaining=$subscribed?PHP_INT_MAX:max(0,FREE_QUESTION_LIMIT-$used);
$visible=[];$newSlots=$remaining;
foreach($rows as $row){
    $id=(int)$row['id'];
    if(!isset($answered[$id])){
        if($newSlots===0)continue;
        $newSlots--;
    }
    $visible[]=$row;
}

$englishMatch=in_array((string)$ch['grade_name'],['السادس العلمي','السادس الأدبي'],true)
    && $ch['subject_name']==='اللغة الإنكليزية'
    && str_contains((string)$ch['name'],'Match');

$questions=[];
foreach($visible as $r){
    $id=(int)$r['id'];
    $optionRows=is_array($r['_answer_rows']??null)?$r['_answer_rows']:[];
    $options=array_map(static fn($x)=>['id'=>(int)($x['id']??0),'text'=>(string)($x['answer_text']??'')],$optionRows);
    if($ch['subject_name']==='اللغة الإنكليزية'&&count($options)>1){
        usort($options,static fn($a,$b)=>strcmp(
            hash('sha256',$u['id'].'|'.$id.'|'.$a['id']),
            hash('sha256',$u['id'].'|'.$id.'|'.$b['id'])
        ));
    }
    $match=murshid_v53_public_matching($r['_english_match']??null);
    $questions[]=[
        'id'=>$id,
        'type'=>(string)$r['type'],
        'answer_mode'=>(string)($r['_answer_mode']??(!empty($options)?'choice':'text')),
        'text'=>public_question_display_text((string)$r['question_text']),
        'meaning_word'=>$r['meaning_word']??null,
        'correct_answer'=>(string)$r['correct_answer'],
        'explanation'=>public_question_explanation($r['explanation']??null,$r['correct_answer']??null),
        'exam_year'=>trim((string)($r['exam_year']??'')),
        'exam_round'=>trim((string)($r['exam_round']??'')),
        'difficulty'=>trim((string)($r['difficulty']??'')),
        'source_label'=>'وزاري',
        'options'=>$options,
        'match_items'=>is_array($r['_match_items']??null)?$r['_match_items']:[],
        'english_match'=>$match,
        'calculation'=>murshid_strip_hints(json_decode((string)($r['calculation_json']??''),true)),
        'genetics'=>murshid_strip_hints(json_decode((string)($r['genetics_json']??''),true)),
        'favorite'=>isset($favoriteIds[$id]),
        'submitted'=>isset($answered[$id]),
        'answer'=>$answered[$id]??null,
        'has_note'=>question_note_for_user($pdo,(int)$u['id'],$id)!=='',
    ];
}

json_response([
    'ok'=>true,
    'csrf'=>csrf_token(),
    'chapter'=>['id'=>(int)$ch['id'],'name'=>$ch['name'],'subject'=>$ch['subject_name']],
    'questions'=>$questions,
    'total'=>count($rows),
    'raw_total'=>count($allRows),
    'filters'=>[
        'type'=>$typeFilter,'year'=>$yearFilter,'round'=>$roundFilter,'difficulty'=>$difficultyFilter,'state'=>$stateFilter,'shuffle'=>$shuffle,
        'available'=>$filterMeta,
    ],
    'subscribed'=>$subscribed,
    'free_used'=>$used,
    'free_remaining'=>$subscribed?null:$remaining,
]);
