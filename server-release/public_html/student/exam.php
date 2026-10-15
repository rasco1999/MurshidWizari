<?php
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
require_once __DIR__ . '/../includes/payments.php';
$examPlans=subscription_plans();
require_once __DIR__ . '/../includes/sixth_english_match.php';
require_once __DIR__ . '/../includes/platform_tools.php';
require_once __DIR__ . '/../includes/hint_free.php';
$u = require_student();
$pdo=db();platform_tools_schema($pdo);
$subscribed = is_subscribed((int)$u['id']);
$freeUsed = free_answers_used((int)$u['id']);
$freeRemaining = $subscribed ? PHP_INT_MAX : max(0, FREE_QUESTION_LIMIT - $freeUsed);
$chapter = (int)($_GET['chapter_id'] ?? 0);

// التقدم مرتبط بالحساب ولا توجد إعادة تصفير للاختبار من واجهة الطالب.
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!verify_csrf($_POST['csrf'] ?? null)) { http_response_code(419); exit('انتهت صلاحية الطلب.'); }
    redirect('/student/exam.php?chapter_id=' . $chapter);
}

$ch=topic_for_student(db(),(int)$u['grade_id'],$chapter);
if(!$ch) { http_response_code(404); exit('الموضوع غير متاح لهذا الصف.'); }
if(preg_match('/فرن|french|franc/iu',(string)($ch['subject_name']??''))){http_response_code(404);exit('المادة غير متاحة.');}
if($chapter!==(int)$ch['id']) redirect('/student/exam.php?chapter_id='.(int)$ch['id']);
$chapter=(int)$ch['id'];$topicIds=topic_id_list($ch);
$chapterDisplayName=$ch['name'];
$isEnglishMatchChapter=(in_array((string)$ch['grade_name'],['السادس العلمي','السادس الأدبي'],true) && (string)$ch['subject_name']==='اللغة الإنكليزية' && str_contains((string)$ch['name'],'Match'));
$live=topic_live_sql('q');
$q=db()->prepare("SELECT q.id,q.chapter_id,q.type,q.question_text,q.meaning_word,q.correct_answer,q.explanation,q.genetics_json,q.calculation_json,q.verification_status,q.exam_year,q.exam_round,(SELECT COUNT(*) FROM answers ax WHERE ax.question_id=q.id) option_count FROM questions q JOIN chapters qc ON qc.id=q.chapter_id JOIN subjects qs ON qs.id=qc.subject_id WHERE q.chapter_id IN ($topicIds) AND qs.grade_id=? AND qs.id=? AND qc.status=1 AND qs.status=1 AND $live ORDER BY q.sort_order,q.id");
$q->execute([(int)$u['grade_id'],(int)$ch['subject_id']]);
$allQuestions=$q->fetchAll(PDO::FETCH_ASSOC)?:[];
// v48: النوع والخيارات والإجابة ومسار التصحيح تُحسم من مصدر واحد.
$allQuestions=function_exists('murshid_v48_prepare_rows')?murshid_v48_prepare_rows($pdo,$allQuestions,true):$allQuestions;
// الإنكليزي: ترتيب ثابت خاص بكل طالب/وحدة، وغير مرتبط بتسلسل صفحات المصادر أو السنة.
if (in_array((string)($ch['subject_name'] ?? ''), ['اللغة الإنكليزية','الأحياء'], true) && count($allQuestions) > 1) {
    $shuffleUid = (int)$u['id'];
    usort($allQuestions, static function(array $a,array $b) use($shuffleUid,$chapter): int {
        $ka = hash('sha256', $shuffleUid.'|'.$chapter.'|'.(int)$a['id']);
        $kb = hash('sha256', $shuffleUid.'|'.$chapter.'|'.(int)$b['id']);
        return strcmp($ka,$kb);
    });
}
$allQuestionCount = count($allQuestions);
$questions = $allQuestions;
$hasMorePaidQuestions = false;
if (!$subscribed) {
    if ($freeRemaining <= 0) {
        $questions = [];
        $hasMorePaidQuestions = $allQuestionCount > 0;
    } else {
$seenQ = db()->prepare("SELECT ae.question_id FROM answer_events ae JOIN questions q ON q.id=ae.question_id WHERE ae.user_id=? AND q.chapter_id IN ($topicIds)");
        $seenQ->execute([(int)$u['id']]);
        $seenIds = array_fill_keys(array_map('intval',$seenQ->fetchAll(PDO::FETCH_COLUMN)),true);
        $allowed=[];$newSlots=$freeRemaining;
        foreach($allQuestions as $row){
            $qid=(int)$row['id'];
            if(isset($seenIds[$qid])){$allowed[]=$row;continue;}
            if($newSlots>0){$allowed[]=$row;$newSlots--;}
            else{$hasMorePaidQuestions=true;}
        }
        $questions=$allowed;
    }
}
$answerMap=[];$matchMap=[];
$questionIds=array_values(array_filter(array_map(static fn($row)=>(int)($row['id']??0),$questions)));
if($questionIds){
    $marks=implode(',',array_fill(0,count($questionIds),'?'));
    $aq=db()->prepare("SELECT question_id,id,answer_text FROM answers WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");
    $aq->execute($questionIds);
    foreach($aq->fetchAll(PDO::FETCH_ASSOC)?:[] as $row)$answerMap[(int)$row['question_id']][]=['id'=>(int)$row['id'],'answer_text'=>$row['answer_text']];
    $mq=db()->prepare("SELECT question_id,id,mad_word,correct_type,reason_text FROM match_items WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");
    $mq->execute($questionIds);
    foreach($mq->fetchAll(PDO::FETCH_ASSOC)?:[] as $row)$matchMap[(int)$row['question_id']][]=['id'=>(int)$row['id'],'mad_word'=>$row['mad_word'],'correct_type'=>$row['correct_type'],'reason_text'=>$row['reason_text']];
}
foreach ($questions as &$question) {
    $question['explanation'] = public_question_explanation($question['explanation'] ?? null,$question['correct_answer'] ?? null);
    $question['exam_round_raw'] = (string)($question['exam_round'] ?? '');
    $question['question_text'] = public_question_display_text($question['question_text'] ?? '');
    $question['ministerial_badge'] = ['label'=>'وزاري','kind'=>'official'];
    $question['exam_round'] = public_exam_round($question['exam_round'] ?? null);
    $question['genetics'] = null; if(($question['type']??'')==='genetics' && !empty($question['genetics_json'])){ $gj=json_decode((string)$question['genetics_json'],true); if(is_array($gj)) $question['genetics']=murshid_strip_hints($gj); } unset($question['genetics_json']);
    $question['calculation'] = null; if(($question['type']??'')==='calculation' && !empty($question['calculation_json'])){ $cj=json_decode((string)$question['calculation_json'],true); if(is_array($cj)) $question['calculation']=murshid_strip_hints($cj); } unset($question['calculation_json']);
    $question['answers'] = is_array($question['_answer_rows']??null) ? array_map(static fn($x)=>['id'=>(int)($x['id']??0),'answer_text'=>(string)($x['answer_text']??'')],$question['_answer_rows']) : ($answerMap[(int)$question['id']] ?? []);
    if ((string)($ch['subject_name'] ?? '') === 'اللغة الإنكليزية' && count($question['answers']) > 1) {
        $shuffleUid = (int)$u['id']; $shuffleQid = (int)$question['id'];
        usort($question['answers'], static function(array $x,array $y) use($shuffleUid,$shuffleQid): int {
            return strcmp(
                hash('sha256',$shuffleUid.'|'.$shuffleQid.'|'.(int)$x['id']),
                hash('sha256',$shuffleUid.'|'.$shuffleQid.'|'.(int)$y['id'])
            );
        });
    }
    $question['match_items'] = is_array($question['_match_items']??null) ? $question['_match_items'] : ($matchMap[(int)$question['id']] ?? []);
    unset($question['_answer_rows'],$question['_match_items'],$question['_answer_mode']);
    if ($isEnglishMatchChapter) {
        $set=sixth_english_match_for_question((string)($question['question_text']??''));
        if ($set) $question['english_match']=['left'=>$set['left']??[],'right'=>$set['right']??[],'map'=>$set['map']??[]];
    }
}
unset($question);

// Load the last saved position for this student/chapter.
$p = db()->prepare("SELECT current_index, answers_json FROM progress WHERE user_id=? AND chapter_id=? LIMIT 1");
$p->execute([(int)$u['id'], $chapter]);
$savedProgress = $p->fetch() ?: null;
$savedIndex = $savedProgress ? (int)$savedProgress['current_index'] : 0;
$savedAnswers = [];
if ($savedProgress && !empty($savedProgress['answers_json'])) {
    $decoded = json_decode($savedProgress['answers_json'], true);
    if (is_array($decoded)) $savedAnswers = $decoded;
}
// الإجابات المسجلة في الحساب هي المرجع النهائي حتى لو مُسح localStorage أو عُدّل الطلب يدوياً.
$eventQ = db()->prepare("SELECT ae.question_id,ae.answer_text,ae.is_correct FROM answer_events ae JOIN questions q ON q.id=ae.question_id WHERE ae.user_id=? AND q.chapter_id IN ($topicIds) ORDER BY ae.id");
$eventQ->execute([(int)$u['id']]);
foreach($eventQ->fetchAll(PDO::FETCH_ASSOC) as $ev){
    $qid=(int)$ev['question_id'];$stored=(string)($ev['answer_text']??'');
    $base=is_array($savedAnswers[$qid]??null)?$savedAnswers[$qid]:[];
    $base['text']=$stored;$base['selected_text']=$stored;$base['submitted']=true;
    // answer_events هو المرجع النهائي دائماً؛ لا نسمح لحالة قديمة داخل progress أن تعرض نتيجة خاطئة.
    $base['is_correct']=(bool)($ev['is_correct']??0);
    $base['grade_status']=$base['is_correct']?'correct':'incorrect';
    $savedAnswers[$qid]=$base;
}
// Recompute position by question ID after imports/merges, not the previous numeric offset.
$savedIndex=0;
foreach($questions as $i=>$question){if(empty($savedAnswers[(int)$question['id']]['submitted'])){$savedIndex=$i;break;}$savedIndex=$i+1;}
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title><?=e($ch['subject_name'])?> | <?=e($chapterDisplayName)?></title>
<script src="<?=BASE_URL?>/assets/js/theme-init.js?v=20260928-v56"></script><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/platform.css?v=20260929-v584contrast"><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/brand-v57.css?v=20260929-v5811responsive">
<link rel="icon" type="image/png" sizes="512x512" href="<?= BASE_URL ?>/assets/img/site-icon-512.png?v=20260928-v56"><link rel="apple-touch-icon" sizes="180x180" href="<?= BASE_URL ?>/assets/img/apple-touch-icon.png?v=20260928-v56">


</head>
<body class="page cinema-study-skin exam-page" data-es-role="student" data-es-base=".." data-es-name="<?=e($u['full_name'])?>">
<div id="contestXpToast" class="contest-xp-toast" role="status" aria-live="polite"></div>
<header class="topbar exam-topbar">
  <a class="exam-brand" href="<?=BASE_URL?>/" aria-label="العودة إلى الرئيسية"><?=ui_icon('book')?><span><b>منصة المرشد الوزاري</b><small>منصة الاختبارات</small></span></a>
  <div class="exam-context"><small><?=e($ch['subject_name'])?></small><strong><?=e($chapterDisplayName)?></strong></div>
  <div class="exam-header-actions"><a class="exam-support" href="stories.php" aria-label="غيّر جو"><?=ui_icon('celebrate')?><span>غيّر جو</span></a><a class="exam-support" href="support.php" aria-label="خدمة العملاء"><?=ui_icon('chat')?><span>الدعم</span></a><a class="exam-home" href="<?=BASE_URL?>/" aria-label="العودة إلى الرئيسية"><?=ui_icon('home')?><span>الرئيسية</span></a></div>
</header>

<main class="container">
  <div id="exam"></div>
</main>

<script>
const QUESTIONS = <?=json_encode($questions, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES)?>;
const MAD_TYPES = <?=json_encode(MAD_TYPES(), JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES)?>;
const CHAPTER_ID = <?=$chapter?>;
const SUBJECT_ID = <?=(int)$ch['subject_id']?>;
const IS_LITERATURE = <?=((string)$ch['subject_name']==='الأدب والنصوص') ? 'true' : 'false'?>;
const IS_ENGLISH_MATCH_CHAPTER = <?=$isEnglishMatchChapter ? 'true' : 'false'?>;
const SUBSCRIBED = <?=$subscribed ? 'true' : 'false'?>;
const CSRF = <?=json_encode(csrf_token())?>;
const FREE_LIMIT = <?=FREE_QUESTION_LIMIT?>;
const FREE_USED_INITIAL = <?=$freeUsed?>;
const FREE_REMAINING_INITIAL = <?=$subscribed ? -1 : $freeRemaining?>;
const HAS_MORE_PAID = <?=$hasMorePaidQuestions ? 'true' : 'false'?>;
const QUOTA_LOCKED_INITIAL = <?=(!$subscribed && $freeRemaining<=0) ? 'true' : 'false'?>;
const FAVORITES = new Set(<?=json_encode(function_exists('favorite_question_ids')?favorite_question_ids($pdo,(int)$u['id']):[])?>);

let index = Math.min(<?=max(0, $savedIndex)?>, QUESTIONS.length);
let quotaLocked = QUOTA_LOCKED_INITIAL;
let answers = <?=json_encode($savedAnswers, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES)?> || {};

function esc(x){
  return String(x ?? '').replace(/[&<>"']/g,m=>({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'
  }[m]));
}

function sameAnswer(userAnswer, correctAnswer){
  return String(userAnswer ?? '').trim().toLowerCase() === String(correctAnswer ?? '').trim().toLowerCase();
}

const englishMatchPending = {};

function englishMatchPairs(a){
  if(a && a.english_match_pairs && typeof a.english_match_pairs==='object') return {...a.english_match_pairs};
  const out={};
  const text=String(a?.text||'');
  for(const m of text.matchAll(/(\d+)\s*-\s*([a-zA-Z])/g)) out[String(m[1])]=String(m[2]).toLowerCase();
  return out;
}

function englishMatchAnswerText(q,pairs){
  const keys=Object.keys(q?.english_match?.left||{}).sort((a,b)=>Number(a)-Number(b));
  return keys.filter(k=>pairs[k]).map(k=>`${k}-${String(pairs[k]).toLowerCase()}`).join(', ');
}

function englishMatchRightNumber(q,key){
  const keys=Object.keys(q?.english_match?.right||{}).sort((a,b)=>String(a).localeCompare(String(b)));
  const i=keys.indexOf(String(key).toLowerCase());
  return i>=0 ? i+1 : '—';
}

function englishMatchComplete(q,pairs){
  const left=Object.keys(q?.english_match?.left||{});
  return left.length>0 && left.every(k=>!!pairs[k]);
}

function englishMatchIsCorrect(q,pairs){
  const map=q?.english_match?.map||{};
  const keys=Object.keys(map);
  return keys.length>0 && keys.every(k=>String(pairs[k]||'').toLowerCase()===String(map[k]||'').toLowerCase());
}

function chooseEnglishMatchLeft(key){
  const q=QUESTIONS[index]; if(!q?.english_match || answers[q.id]?.submitted) return;
  const a=answers[q.id]||{submitted:false}; const pairs=englishMatchPairs(a);
  key=String(key); if(pairs[key]) return;
  englishMatchPending[q.id]=key; a.english_match_pairs=pairs; answers[q.id]=a; saveLocal(); render();
}

function chooseEnglishMatchRight(key){
  const q=QUESTIONS[index]; if(!q?.english_match || answers[q.id]?.submitted) return;
  const a=answers[q.id]||{submitted:false}; const pairs=englishMatchPairs(a); key=String(key).toLowerCase();
  const pending=String(englishMatchPending[q.id]||'');
  if(!pending){ alert('اختر عنصراً من القائمة A أولاً.'); return; }
  if(Object.values(pairs).map(v=>String(v).toLowerCase()).includes(key)) return;
  pairs[pending]=key; delete englishMatchPending[q.id];
  a.english_match_pairs=pairs; a.text=englishMatchAnswerText(q,pairs); answers[q.id]=a; saveLocal(); render();
}

function editEnglishMatchPair(leftKey){
  const q=QUESTIONS[index]; if(!q?.english_match || answers[q.id]?.submitted) return;
  const a=answers[q.id]||{submitted:false}; const pairs=englishMatchPairs(a); leftKey=String(leftKey);
  delete pairs[leftKey]; englishMatchPending[q.id]=leftKey;
  a.english_match_pairs=pairs; a.text=englishMatchAnswerText(q,pairs); answers[q.id]=a; saveLocal(); render();
}

function matchState(a){
  if (a && a.match && typeof a.match === 'object') return a.match;
  if (a && a.text) {
    try {
      const parsed = JSON.parse(a.text);
      if (parsed && typeof parsed === 'object') return parsed.items || parsed;
    } catch(e) {}
  }
  return {};
}

function setMatchValue(itemId, key, value){
  const q = QUESTIONS[index];
  if (answers[q.id]?.submitted) return;
  const a = answers[q.id] || {submitted:false};
  const state = matchState(a);
  const k = String(itemId);
  state[k] = state[k] || {type:'',reason:''};
  state[k][key] = value;
  a.match = state;
  a.text = JSON.stringify({items:state});
  answers[q.id] = a;
  saveLocal();
}

function matchIsCorrect(q,a){
  const state=matchState(a);
  if(!q.match_items || !q.match_items.length) return false;
  return q.match_items.every(item=>{
    const g=state[String(item.id)]||{};
    return String(g.type||'').trim()===String(item.correct_type||'').trim() && String(g.reason||'').trim()!=='';
  });
}

function calcState(a){
  if(a && a.calculation && typeof a.calculation==='object') return a.calculation;
  if(a && a.text){try{const j=JSON.parse(a.text);if(j&&j.stages)return j.stages;}catch(e){}}
  return {};
}
function calcNorm(v){return String(v??'').trim().toLowerCase().replace(/[٠-٩]/g,d=>'٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/٫/g,'.').replace(/\s+/g,'').replace(/[×x]/g,'x').replace(/[−–]/g,'-');}
function calcChoiceOk(stage,value){const vals=Array.isArray(stage.answers)?stage.answers:[stage.answer];return vals.some(v=>calcNorm(v)===calcNorm(value));}
// The live validation must follow server numeric rules; never extract the first number from invalid input.
function calcMathValue(raw){
  let x=String(raw??'').trim().replace(/[٠-٩]/g,d=>String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)))
    .replace(/٫/g,'.').replace(/[−–]/g,'-').replace(/[×·]/g,'*').replace(/÷/g,'/')
    .replace(/√/g,'sqrt').replace(/π/g,'pi').replace(/²/g,'^2').replace(/³/g,'^3')
    .replace(/(?<=\d)[xX](?=10\s*\^)/g,'*').toLowerCase().replace(/\s+/g,'');
  if(!x || x.length>280) return null;
  const tokenRx=/(?:\d+(?:\.\d+)?(?:e[+\-]?\d+)?|\.\d+(?:e[+\-]?\d+)?|[a-z]+|[()+*\/^\-])/gi;
  const rawTokens=x.match(tokenRx)||[];
  if(rawTokens.join('')!==x||rawTokens.length>120||rawTokens.some(t=>/^[a-z]+$/.test(t)&&!['pi','sqrt'].includes(t))) return null;
  const tokens=[];let prev=null;
  for(const t of rawTokens){
    const ends=prev!==null&&(prev===')'||prev==='pi'||/^(?:\d|\.)/.test(prev));
    const starts=t==='('||t==='sqrt'||t==='pi'||/^(?:\d|\.)/.test(t);
    if(ends&&starts)tokens.push('*');
    tokens.push(t);prev=t;
  }
  let pos=0;
  function parse(minBp=0){
    if(pos>=tokens.length)throw Error('empty');
    const first=tokens[pos++];let left;
    if(first==='+'||first==='-')left=(first==='-'?-1:1)*parse(6);
    else if(first==='('){left=parse(0);if(tokens[pos++]!==')')throw Error('paren');}
    else if(first==='sqrt'){
      let value;
      if(tokens[pos]==='('){pos++;value=parse(0);if(tokens[pos++]!==')')throw Error('sqrt');}
      else value=parse(6);
      if(value<0)throw Error('root');left=Math.sqrt(value);
    }
    else if(first==='pi')left=Math.PI;
    else if(/^\d|^\./.test(first))left=Number(first);
    else throw Error('token');
    while(pos<tokens.length){
      const op=tokens[pos];let leftBp,rightBp;
      if(op==='+'||op==='-'){leftBp=1;rightBp=2;}
      else if(op==='*'||op==='/'){leftBp=3;rightBp=4;}
      else if(op==='^'){leftBp=7;rightBp=7;}
      else break;
      if(leftBp<minBp)break;
      pos++;const right=parse(rightBp);
      if(op==='+')left+=right;
      if(op==='-')left-=right;
      if(op==='*')left*=right;
      if(op==='/'){if(Math.abs(right)<1e-15)throw Error('divide');left/=right;}
      if(op==='^'){if(Math.abs(right)>40)throw Error('power');left=Math.pow(left,right);}
      if(!Number.isFinite(left)||Math.abs(left)>1e100)throw Error('number');
    }
    return left;
  }
  try{const result=parse();return pos===tokens.length&&Number.isFinite(result)?result:null;}
  catch(_){return null;}
}
function calcNumberValue(v,expectedUnit=''){
  const value=calcMathValue(v);if(value!==null)return value;
  if(!expectedUnit)return null;
  const groups=[
    {F:1,mF:1e-3,'μF':1e-6,'µF':1e-6,uF:1e-6,nF:1e-9,pF:1e-12},
    {C:1,mC:1e-3,'μC':1e-6,'µC':1e-6,uC:1e-6},
    {V:1,mV:1e-3,kV:1000},{A:1,mA:1e-3,'μA':1e-6},
    {'Ω':1,'kΩ':1000,'MΩ':1e6,ohm:1},
    {m:1,cm:.01,mm:.001,km:1000},
    {'m²':1,'cm²':.0001,'mm²':.000001},
    {J:1,kJ:1000},{W:1,kW:1000},{Hz:1,kHz:1000}
  ];
  const group=groups.find(g=>Object.hasOwn(g,expectedUnit))||{[expectedUnit]:1};
  const expr=String(v??'').trim();
  for(const unit of Object.keys(group).sort((a,b)=>b.length-a.length)){
    if(!expr.endsWith(unit))continue;
    const numeric=calcMathValue(expr.slice(0,-unit.length).trim());
    if(numeric!==null)return numeric*group[unit]/group[expectedUnit];
  }
  return null;
}
function calcStageLiveOk(stage,value){
  if(stage.kind==='info') return true;
  if(stage.kind==='number'){
    const unit=String(stage.unit||'');
    const g=calcNumberValue(value,unit), vals=Array.isArray(stage.answers)?stage.answers:[stage.answer], tol=Number(stage.tolerance??0.001);
    if(g===null||!Number.isFinite(g)) return false;
    return vals.some(v=>{const a=calcNumberValue(v,unit); if(a===null||!Number.isFinite(a))return false; const scale=Math.max(1,Math.abs(a)); return Math.abs(g-a)<=Math.max(tol,1e-8*scale);});
  }
  return calcChoiceOk(stage,value);
}
function calcPreviousReady(stages,st,idx){
  for(let i=0;i<idx;i++){
    const x=stages[i]; if(x.kind==='info'||x.ungraded) continue;
    const v=String(st[String(x.key)]??'').trim(); if(!v||!calcStageLiveOk(x,v)) return false;
  }
  return true;
}
function setCalcValue(key,value){
  const q=QUESTIONS[index]; if(!q || answers[q.id]?.submitted) return;
  const a=answers[q.id]||{}; const st=calcState(a); st[String(key)]=String(value??''); a.calculation=st; a.text=JSON.stringify({stages:st});
  const stage=(q.calculation?.stages||[]).find(x=>String(x.key)===String(key));
  if(stage && stage.kind!=='info'){
    const ok=calcStageLiveOk(stage,value);
    a.calc_step_feedback={key:String(key),ok,message:ok?'صحيح ✓ انتقل إلى المرحلة التالية.':'إجابة غير صحيحة. يمكنك تعديلها والمحاولة مجددًا.'};
  }
  answers[q.id]=a; saveLocal(); render();
}
function calcStageComplete(stage,st){
  if(stage.kind==='info'||stage.ungraded) return true;
  return String(st[String(stage.key)]??'').trim()!=='';
}
function calculationComplete(q,a){ const st=calcState(a); return (q.calculation?.stages||[]).every(stage=>calcStageComplete(stage,st)); }
function calcFeedback(q,a){
  const def=q.calculation||{}, st=calcState(a), details=a?.grade_details||{};
  const score=Number(a?.grade_score||0); let rows='';
  (def.stages||[]).forEach(stage=>{
    if(stage.kind==='info') return;
    const d=details[String(stage.key)]||{}; const given=String(st[String(stage.key)]||'—');
    const ok=!!d.ok; const correct=String(d.correct??stage.display_answer??'');
    rows+=`<div class="calc-feedback-row ${ok?'is-correct':'is-wrong'}"><b>${esc(stage.label||stage.key)}</b><span>إجابتك: ${esc(given)}</span>${correct?`<span>الصحيح: ${esc(correct)}</span>`:''}</div>`;
  });
  return `<div class="calc-result ${score===100?'good':'needs-work'}"><div class="calc-score">إتقان المسألة: <b>${score}%</b></div>${rows}${def.model_solution?`<details class="model-solution"><summary>عرض الحل الوزاري النموذجي</summary><div>${esc(def.model_solution).replace(/\n/g,'<br>')}</div></details>`:''}</div>`;
}
function geneticsState(a){
  if(a && a.genetics && typeof a.genetics==='object') return a.genetics;
  try{ const p=JSON.parse(String(a?.text||'')); if(p&&p.stages) return p.stages; }catch(e){}
  return {};
}
function setGeneticsValue(key,value){
  const q=QUESTIONS[index]; if(!q || answers[q.id]?.submitted) return;
  const a=answers[q.id]||{}; const st=geneticsState(a); st[String(key)]=String(value||''); a.genetics=st; a.text=JSON.stringify({stages:st}); answers[q.id]=a; saveLocal();
}
function geneticsComplete(q,a){
  const st=geneticsState(a); const stages=q.genetics?.stages||[];
  if(!stages.length) return false;
  return stages.every(x=>{ if(x.kind==='punnett'&&Array.isArray(x.cells)) return x.cells.every((c,i)=>String(st[String(x.key)+'_'+i]||'').trim()!==''); return String(st[String(x.key)]||'').trim()!==''; });
}
function normalizeGenetic(v){ return String(v??'').replace(/\s+/g,'').replace(/[×xX]/g,'×').toLowerCase(); }
function geneticsLocalScore(q,a){
  const st=geneticsState(a), stages=q.genetics?.stages||[]; let got=0,total=0;
  stages.forEach(x=>{ if(x.kind==='punnett'&&Array.isArray(x.cells)){ x.cells.forEach((c,i)=>{total++;const vals=Array.isArray(c.answers)?c.answers:[c.answer];if(vals.some(v=>normalizeGenetic(v)===normalizeGenetic(st[String(x.key)+'_'+i]||'')))got++;}); } else {total++; const vals=Array.isArray(x.answers)?x.answers:[x.answer]; if(vals.some(v=>normalizeGenetic(v)===normalizeGenetic(st[String(x.key)]||''))) got++;} });
  return {got,total,score:total?Math.round(got/total*100):0};
}
function geneticsFeedback(q,a){
  const st=geneticsState(a), stages=q.genetics?.stages||[]; const score=Number(a?.grade_score ?? geneticsLocalScore(q,a).score);
  let rows=''; stages.forEach(x=>{ if(x.kind==='punnett'&&Array.isArray(x.cells)){x.cells.forEach((c,i)=>{const vals=Array.isArray(c.answers)?c.answers:[c.answer];const given=String(st[String(x.key)+'_'+i]||'');const ok=vals.some(v=>normalizeGenetic(v)===normalizeGenetic(given));rows+=`<div class="genetics-feedback-row ${ok?'is-correct':'is-wrong'}"><b>${esc((x.label||'مربع بانيت')+' — خانة '+(i+1))}</b><span>إجابتك: ${esc(given||'—')}</span><span>الصحيح: ${esc(vals[0]||'—')}</span></div>`;});}else{const vals=Array.isArray(x.answers)?x.answers:[x.answer]; const given=String(st[String(x.key)]||''); const ok=vals.some(v=>normalizeGenetic(v)===normalizeGenetic(given)); rows+=`<div class="genetics-feedback-row ${ok?'is-correct':'is-wrong'}"><b>${esc(x.label||x.key)}</b><span>إجابتك: ${esc(given||'—')}</span><span>الصحيح: ${esc(vals[0]||'—')}</span></div>`;}});
  return `<div class="answer-feedback ${score===100?'correct':'wrong'}"><div class="feedback-title">نتيجة خطوات المسألة: ${score}%</div>${rows}${q.explanation?`<div class="explanation"><b>ملاحظة:</b> ${esc(q.explanation)}</div>`:''}</div>`;
}

function feedback(q, a){
  const userText = a?.text ?? a?.selected_text ?? '';

  if(q.type==='calculation') return calcFeedback(q,a);
  if(q.type==='genetics') return geneticsFeedback(q,a);

  if (q.english_match) {
    const pairs=englishMatchPairs(a); const ok=englishMatchIsCorrect(q,pairs); const map=q.english_match.map||{};
    let rows='';
    Object.keys(q.english_match.left||{}).sort((x,y)=>Number(x)-Number(y)).forEach(k=>{
      const got=String(pairs[k]||'').toLowerCase(); const right=String(map[k]||'').toLowerCase(); const good=got===right;
      const leftText=q.english_match.left[k]||''; const rightText=(q.english_match.right||{})[got]||'—'; const correctText=(q.english_match.right||{})[right]||'—';
      rows += `<div class="english-match-feedback-row ${good?'is-correct':'is-wrong'}"><b>A${esc(k)} — ${esc(leftText)}</b><span>اختيارك: B${esc(englishMatchRightNumber(q,got))} — ${esc(rightText)}</span>${good?'':`<span>الصحيح: B${esc(englishMatchRightNumber(q,right))} — ${esc(correctText)}</span>`}</div>`;
    });
    return `<div class="answer-feedback ${ok?'correct':'wrong'}"><div class="feedback-title">${ok?'جميع التوصيلات صحيحة':'توجد توصيلات غير صحيحة'}</div>${rows}</div>`;
  }

  if (q.type === 'match') {
    const state=matchState(a); const ok=matchIsCorrect(q,a);
    let rows='';
    (q.match_items||[]).forEach(item=>{
      const g=state[String(item.id)]||{};
      const typeOk=String(g.type||'').trim()===String(item.correct_type||'').trim();
      rows += `<div class="tajweed-feedback-row"><b>${esc(item.mad_word)}</b><div>اختيارك: <span class="${typeOk?'ok-text':'bad-text'}">${esc(g.type||'—')}</span></div><div>النوع الصحيح: <b>${esc(item.correct_type)}</b></div><div>سببك: ${esc(g.reason||'—')}</div><div>السبب النموذجي: <b>${esc(item.reason_text||'—')}</b></div></div>`;
    });
    return `<div class="answer-feedback ${ok?'correct':'wrong'}"><div class="feedback-title">${ok?'نوع المد صحيح':'نوع المد أو السبب غير صحيح'}</div>${rows}</div>`;
  }

  // الاختيار من متعدد فقط: تصحيح فعلي (صح/خطأ)
  if (q.type === 'mcq' || (q.type === 'meaning' && q.answers && q.answers.length) || q.type === 'true_false') {
    const isCorrect = sameAnswer(userText, q.correct_answer);
    return `<div class="answer-feedback ${isCorrect ? 'correct' : 'wrong'}">
      <div class="feedback-title">${isCorrect ? 'إجابة صحيحة' : 'إجابة غير صحيحة'}</div>
      ${userText ? `<div><b>إجابتك:</b> ${esc(userText)}</div>` : ''}
      <div><b>الإجابة الصحيحة:</b> ${esc(q.correct_answer)}</div>
      ${q.explanation ? `<div class="explanation"><b>التوضيح:</b> ${esc(q.explanation)}</div>` : ''}
    </div>`;
  }

  // الأسئلة النصية: الحكم يأتي من محرك التصحيح العربي في الخادم.
  const status = a?.grade_status || (a?.is_correct ? 'correct' : 'incorrect');
  const accepted = status === 'accepted';
  const ok = status === 'correct' || accepted;
  const title = ok ? 'إجابة صحيحة' : 'إجابة غير صحيحة';
  return `<div class="answer-feedback ${ok ? 'correct' : 'wrong'}">
    <div class="feedback-title">${title}</div>
    ${userText ? `<div><b>إجابتك:</b> ${esc(userText)}</div>` : ''}
    <div><b>الإجابة النموذجية:</b> ${esc(q.correct_answer).replace(/\n/g,'<br>')}</div>
    ${q.explanation ? `<div class="explanation"><b>التوضيح:</b> ${esc(q.explanation)}</div>` : ''}
  </div>`;
}

function render(){
  const root = document.querySelector('#exam');

  // لا نقفل الحساب لمجرد أن الوحدة تحتوي أسئلة أكثر من الرصيد المجاني.
  // HAS_MORE_PAID يعني أن هناك أسئلة إضافية *بعد* الأسئلة المجانية الحالية،
  // وليس أن الطالب استهلك رصيده. القفل الفوري يعتمد فقط على العداد الحقيقي.
  if (!SUBSCRIBED && (quotaLocked || FREE_REMAINING_INITIAL <= 0)) {
    root.innerHTML = `<div class="paywall premium-paywall">
      <div class="paywall-lock"><?=ui_icon('lock')?></div>
      <h2>أكملت <?=FREE_QUESTION_LIMIT?> سؤالاً مجانياً</h2>
      <p>للمتابعة في جميع المواد والاختبارات يلزم تفعيل الاشتراك.</p>
      <p><strong>شهر واحد: <?=number_format((int)$examPlans['month1']['amount'])?> د.ع • 3 أشهر: <?=number_format((int)$examPlans['month3']['amount'])?> د.ع</strong></p>
      <a class="primary-btn" style="display:inline-flex" href="../subscription.php">الانتقال إلى الاشتراك والدفع</a>
    </div>`;
    return;
  }

  if (!QUESTIONS.length) {
    root.innerHTML = `<div class="card empty-state"><h2>لا توجد أسئلة بعد</h2><p>لا توجد أسئلة منشورة لهذا الموضوع حالياً.</p></div>`;
    return;
  }

  // وصل الطالب إلى نهاية الجزء المجاني المعروض، وتوجد أسئلة أخرى في الوحدة:
  // هنا فقط نظهر الاشتراك، بعد أن يكون قد أجاب فعلياً عن الأسئلة المتاحة له.
  if (index >= QUESTIONS.length && !SUBSCRIBED && HAS_MORE_PAID) {
    root.innerHTML = `<div class="paywall premium-paywall">
      <div class="paywall-lock"><?=ui_icon('lock')?></div>
      <h2>أكملت <?=FREE_QUESTION_LIMIT?> سؤالاً مجانياً</h2>
      <p>تم حفظ إجاباتك، وللمتابعة في بقية الأسئلة والمواد يلزم تفعيل الاشتراك.</p>
      <p><strong>شهر واحد: <?=number_format((int)$examPlans['month1']['amount'])?> د.ع • 3 أشهر: <?=number_format((int)$examPlans['month3']['amount'])?> د.ع</strong></p>
      <a class="primary-btn" style="display:inline-flex" href="../subscription.php">الانتقال إلى الاشتراك والدفع</a>
    </div>`;
    return;
  }

  if (index >= QUESTIONS.length) {
    root.innerHTML = `<div class="card exam-finished-card">
      <div class="finish-icon"><?=ui_icon('celebrate')?></div>
      <h2>انتهت أسئلة الموضوع</h2>
      <p>تم إكمال جميع الأسئلة المتاحة في هذا الموضوع.</p>
      <p class="muted">تم حفظ التقدم والإجابات في الحساب.</p>
      <div class="new-question-request-box">
        <h3><?=ui_icon('plus')?> طلب أسئلة إضافية</h3>
        <p>يمكن إرسال طلب إضافة أسئلة جديدة بعد إكمال الأسئلة الحالية.</p>
        <div class="question-request-actions" id="questionRequestActions">
          <button type="button" class="primary-btn" id="requestChapterBtn" onclick="requestNewQuestions('chapter')" hidden>طلب أسئلة جديدة لهذا الموضوع</button>
          <span id="chapterRequestState" class="request-state">جارٍ التحقق…</span>
          <div id="subjectRequestAction"></div>
        </div>
      </div>
      <div class="finish-actions">
        <a class="primary-btn" href="review.php"><?=ui_icon('brain')?> المراجعة الذكية</a>
        <a class="secondary-btn" href="<?=BASE_URL?>/"><?=ui_icon('home')?> الرئيسية</a>
      </div>
    </div>`;
    refreshRequestEligibility();
    return;
  }

  const q = QUESTIONS[index];
  const a = answers[q.id] || {};
  const saving = !!a.saving;
  const submitted = !!a.submitted && !saving;
  const locked = !!a.submitted || saving;
  const instantChoice = !q.english_match && (q.type === 'mcq' || q.type === 'true_false' || (q.type === 'meaning' && q.answers && q.answers.length));
  const prompt = q.english_match ? 'صِل كل عنصر من القائمة A بالجواب المناسب من القائمة B' : (q.type === 'meaning' && q.meaning_word ? `ما معنى كلمة: <strong>${esc(q.meaning_word)}</strong>؟` : esc(q.question_text).replace(/\n/g,'<br>'));

  const progressPct = QUESTIONS.length ? Math.round(((index+1)/QUESTIONS.length)*100) : 0;
  let html = `<div class="question-card exam-question-card">
    ${IS_LITERATURE ? '<div class="question-source-author">سعد خلف الحمراني</div>' : ''}
    <div class="question-meta"><div class="question-counter"><span>السؤال</span><b>${index+1}<small> من ${QUESTIONS.length}</small></b></div><span class="q-verified">وزاري</span></div>
    <div class="question-tools" aria-label="أدوات السؤال"><button type="button" class="question-tool ${FAVORITES.has(Number(q.id))?'active':''}" onclick="toggleFavorite(${Number(q.id)})">${FAVORITES.has(Number(q.id))?'★ محفوظ':'☆ حفظ'}</button><button type="button" class="question-tool" onclick="reportQuestion(${Number(q.id)})">⚑ إبلاغ</button></div>
    <div class="exam-progress" aria-label="تقدم الاختبار ${progressPct}%"><i style="width:${progressPct}%"></i></div>

    <div class="question-text" dir="auto">${prompt}</div>
    <div class="answers">`;

  if (q.english_match) {
    const pairs=englishMatchPairs(a); const usedRights=new Set(Object.values(pairs).map(v=>String(v).toLowerCase()));
    const pending=String(englishMatchPending[q.id]||'');
    const leftEntries=Object.entries(q.english_match.left||{}).sort((x,y)=>Number(x[0])-Number(y[0]));
    const rightEntries=Object.entries(q.english_match.right||{}).sort((x,y)=>String(x[0]).localeCompare(String(y[0])));
    if(pending){
      const pendingText=(q.english_match.left||{})[pending]||'';
      html += `<div class="english-match-pending" role="status">اختر الآن التعريف المناسب لـ <b>A${esc(pending)}</b><span>${esc(pendingText)}</span></div>`;
    }
    html += `<div class="english-match-board" dir="ltr"><section class="english-match-column english-match-left"><div class="english-match-column-title"><b>List A</b><span>الكلمات</span></div>`;
    leftEntries.forEach(([k,text])=>{
      const used=!!pairs[k], active=pending===String(k);
      html += `<button type="button" class="english-match-choice ${used?'used':''} ${active?'selected':''}" ${submitted||used?'disabled':''} onclick="chooseEnglishMatchLeft('${esc(k)}')"><span class="english-match-key">A${esc(k)}</span><span class="english-match-text">${esc(text)}</span>${used?'<span class="english-match-done">✓</span>':''}</button>`;
    });
    html += `</section><section class="english-match-column english-match-right"><div class="english-match-column-title"><b>List B</b><span>التعريفات</span></div>`;
    rightEntries.forEach(([k,text],ri)=>{
      const used=usedRights.has(String(k).toLowerCase());
      html += `<button type="button" class="english-match-choice ${used?'used':''}" ${submitted||used?'disabled':''} onclick="chooseEnglishMatchRight('${esc(k)}')"><span class="english-match-key">B${ri+1}</span><span class="english-match-text">${esc(text)}</span>${used?'<span class="english-match-done">✓</span>':''}</button>`;
    });
    html += `</section></div>`;
    if(Object.keys(pairs).length){
      html += `<div class="english-match-pairs"><div class="english-match-pairs-title"><b>التوصيلات المختارة</b><span>${Object.keys(pairs).length} من ${leftEntries.length}</span></div>`;
      leftEntries.filter(([k])=>pairs[k]).forEach(([k,ltext])=>{
        const r=String(pairs[k]).toLowerCase(),rtext=(q.english_match.right||{})[r]||'';
        html += `<div class="english-match-pair" dir="ltr"><div class="english-match-pair-side"><span class="english-match-key">A${esc(k)}</span><span class="english-match-text">${esc(ltext)}</span></div><span class="english-match-arrow" aria-hidden="true">↔</span><div class="english-match-pair-side"><span class="english-match-key">B${esc(englishMatchRightNumber(q,r))}</span><span class="english-match-text">${esc(rtext)}</span></div>${submitted?'':`<button type="button" class="english-match-edit" onclick="editEnglishMatchPair('${esc(k)}')">تعديل</button>`}</div>`;
      });
      html += `</div>`;
    }
  } else if (q.type === 'calculation') {
    const st=calcState(a); const def=q.calculation||{}; const stages=def.stages||[];
    const labels={physics:'فيزياء',chemistry:'كيمياء',math:'رياضيات'};
    html += `<div class="calc-workspace"><div class="calc-head"><span class="calc-brand">الحل الوزاري التفاعلي</span><span>${esc(labels[def.subject]||'مسألة')}</span></div>`;
    if(def.instructions) html += `<div class="calc-instructions">${esc(def.instructions)}</div>`;
    stages.forEach((stage,si)=>{
      const key=String(stage.key||('stage'+si)), val=String(st[key]||'');
      const unlocked=submitted || calcPreviousReady(stages,st,si) || stage.kind==='info';
      const liveOk=val!=='' && stage.kind!=='info' ? calcStageLiveOk(stage,val) : false;
      html += `<section class="calc-stage ${stage.kind==='info'?'is-info':''} ${unlocked?'':'is-locked'}"><div class="calc-stage-title"><span>${si+1}</span><b>${esc(stage.label||key)}</b>${liveOk&&!submitted?'<em class="calc-stage-badge ok">✓ صحيحة</em>':(!unlocked?'<em class="calc-stage-badge">مقفلة</em>':'')}</div>`;
      if(stage.prompt) html += `<div class="calc-stage-prompt">${esc(stage.prompt)}</div>`;
      if(!unlocked){
        html += `<div class="calc-stage-lock">أكمل المرحلة السابقة بصورة صحيحة أولاً.</div>`;
      }else if(stage.kind==='info'){
        html += `<div class="calc-info-box">${esc(stage.value||'').replace(/\n/g,'<br>')}</div>`;
      }else if(stage.kind==='choice'){
        html += `<div class="calc-choice-grid">`; (stage.options||[]).forEach(opt=>{const vv=String(opt.value??opt); const lab=String(opt.label??opt); html += `<button type="button" class="calc-choice ${val===vv?'selected':''}" ${submitted?'disabled':''} onclick="setCalcValue('${esc(key)}','${esc(vv)}')">${esc(lab)}</button>`;}); html += `</div>`;
      }else{
        const inputMode=stage.kind==='number'?'decimal':'text';
        html += `<div class="calc-input-row"><input inputmode="${inputMode}" ${submitted?'disabled':''} value="${esc(val)}" placeholder="${esc(stage.placeholder||'اكتب الإجابة')}" onchange="setCalcValue('${esc(key)}',this.value)" onblur="setCalcValue('${esc(key)}',this.value)">${stage.unit?`<span class="calc-unit">${esc(stage.unit)}</span>`:''}</div>`;
      }
      if(!submitted && unlocked && a.calc_step_feedback && a.calc_step_feedback.key===key) html += `<div class="calc-live-feedback ${a.calc_step_feedback.ok?'ok':'bad'}">${esc(a.calc_step_feedback.message)}</div>`;
      html += `</section>`;
    });
    html += `</div>`;
  } else if (q.type === 'genetics') {
    const st=geneticsState(a); const stages=q.genetics?.stages||[];
    html += `<div class="genetics-workspace"><div class="genetics-intro">حلّ المسألة خطوة بخطوة. يمكنك كتابة الرموز والطراز الوراثي والأمشاج والنتيجة، وسيُصحح كل جزء منفصلاً.</div>`;
    stages.forEach((stage,si)=>{
      const val=String(st[String(stage.key)]||'');
      if(stage.kind==='punnett' && Array.isArray(stage.cells)){
        html += `<div class="genetics-stage"><label>${esc(stage.label||'مربع بانيت')}</label><div class="punnett-grid" style="--punnett-cols:${stage.cols||2}">`;
        stage.cells.forEach((cell,ci)=>{const k=String(stage.key)+'_'+ci; const vv=String(st[k]||''); html += `<input ${submitted?'disabled':''} value="${esc(vv)}" placeholder="${esc(cell.placeholder||'')}" oninput="setGeneticsValue('${esc(k)}',this.value)">`;});
        html += `</div></div>`;
      } else {
        html += `<div class="genetics-stage"><label>${esc(stage.label||stage.key)}</label><input type="text" ${submitted?'disabled':''} value="${esc(val)}" placeholder="${esc(stage.placeholder||'اكتب الإجابة')}" oninput="setGeneticsValue('${esc(stage.key)}',this.value)"></div>`;
      }
    });
    html += `</div>`;
  } else if (q.type === 'match') {
    const state=matchState(a);
    html += `<div class="tajweed-match-list">`;
    (q.match_items||[]).forEach(item=>{
      const g=state[String(item.id)]||{};
      html += `<div class="tajweed-match-row">
        <div class="tajweed-word-box"><span>الكلمة</span><strong>${esc(item.mad_word)}</strong></div>
        <label class="tajweed-field"><span>نوع المد</span><select ${submitted?'disabled':''} onchange="setMatchValue(${item.id},'type',this.value)">
          <option value="">اختر نوع المد</option>${MAD_TYPES.map(t=>`<option value="${esc(t)}" ${String(g.type||'')===t?'selected':''}>${esc(t)}</option>`).join('')}
        </select></label>
        <label class="tajweed-field tajweed-reason"><span>اكتب السبب</span><input type="text" ${submitted?'disabled':''} value="${esc(g.reason||'')}" placeholder="اذكر سبب نوع المد" oninput="setMatchValue(${item.id},'reason',this.value)"></label>
      </div>`;
    });
    html += `</div>`;
  } else if (q.type === 'mcq' || (q.type === 'meaning' && q.answers && q.answers.length)) {
    q.answers.forEach(x => {
      const selected = a.selected == x.id || sameAnswer(a.selected_text || a.text, x.answer_text);
      const disabled = locked ? 'disabled' : '';
      let cls = selected ? 'selected' : '';
      if (submitted) {
        if (sameAnswer(x.answer_text, q.correct_answer)) cls = 'correct';
        else if (selected) cls = 'wrong';
      }
      html += `<button type="button" class="answer-option ${cls}" ${disabled}
        data-instant-answer="1" data-answer-id="${esc(x.id)}" data-answer-text="${esc(x.answer_text)}" dir="auto">${esc(x.answer_text)}</button>`;
    });
  } else if (q.type === 'meaning') {
    html += `<input type="text" id="textAnswer" class="exam-text-answer" ${submitted ? 'disabled' : ''} placeholder="اكتب معنى الكلمة هنا..." value="${esc(a.text || '')}">`;
  } else if (q.type === 'true_false') {
    q.answers.forEach(x => {
      const selected = a.selected == x.id || sameAnswer(a.selected_text || a.text, x.answer_text);
      const disabled = locked ? 'disabled' : '';
      let cls = selected ? 'selected' : '';
      if (submitted) {
        if (sameAnswer(x.answer_text, q.correct_answer)) cls = 'correct';
        else if (selected) cls = 'wrong';
      }
      html += `<button type="button" class="answer-option ${cls}" ${disabled}
        data-instant-answer="1" data-answer-id="${esc(x.id)}" data-answer-text="${esc(x.answer_text)}" dir="auto">${esc(x.answer_text)}</button>`;
    });
  } else if (q.type === 'fill') {
    // إكمال فراغ: خانة واحدة فقط
    html += `<input type="text" id="textAnswer" class="exam-text-answer" ${submitted ? 'disabled' : ''} placeholder="اكتب الإجابة هنا..." value="${esc(a.text || '')}">`;
  } else {
    // شرح نصي
    html += `<textarea id="textAnswer" class="exam-text-answer" dir="auto" ${submitted ? 'disabled' : ''} placeholder="اكتب إجابتك هنا...">${esc(a.text || '')}</textarea>`;
  }

  html += `</div>`;

  if (saving) {
    html += `<div class="answer-saving" role="status">جاري التحقق من الإجابة وحفظها في حسابك…</div>`;
  } else if (!submitted && q.english_match) {
    const pairState=englishMatchPairs(a); const complete=englishMatchComplete(q,pairState);
    html += `<button type="button" class="primary-btn english-match-submit" onclick="submitAnswer()" ${complete?'':'disabled'}>${complete?'تسليم التوصيلات':'أكمل جميع التوصيلات أولاً'}</button>`;
  } else if (!submitted && !instantChoice) {
    html += `<button type="button" class="primary-btn" onclick="submitAnswer()">تسليم الإجابة</button>`;
  } else if (!submitted && instantChoice) {
    // No pre-answer hints: selecting a choice triggers immediate grading.
  } else {
    html += feedback(q, a);
  }

  html += `<div class="nav-buttons">`;
  html += `<button type="button" class="secondary-btn" onclick="move(-1)" ${index === 0 || saving ? 'disabled' : ''}>السؤال السابق</button>`;
  if (submitted) {
    html += `<button type="button" class="primary-btn" onclick="move(1)">${index === QUESTIONS.length-1 ? 'إنهاء' : 'السؤال التالي →'}</button>`;
  } else {
    html += `<button type="button" class="secondary-btn" disabled>${saving ? 'جاري الحفظ…' : 'أجب أولاً'}</button>`;
  }
  html += `</div></div>`;

  root.innerHTML = html;
  root.querySelectorAll('[data-instant-answer="1"]').forEach(btn => {
    btn.addEventListener('click', () => chooseAnswerAndSubmit(Number(btn.dataset.answerId || 0), btn.dataset.answerText || ''));
  });
}

async function questionTool(action,questionId,extra={}){
  const response=await fetch('../api/question-tools.php',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,question_id:questionId,csrf:CSRF,...extra})});
  const data=await response.json().catch(()=>({ok:false,message:'تعذر قراءة الاستجابة.'}));
  if(!response.ok||!data.ok)throw new Error(data.message||'تعذر تنفيذ الطلب.');return data;
}
async function toggleFavorite(questionId){try{const active=FAVORITES.has(Number(questionId));const data=await questionTool(active?'unfavorite':'favorite',questionId);if(data.favorite)FAVORITES.add(Number(questionId));else FAVORITES.delete(Number(questionId));render();}catch(e){alert(e.message);}}
async function reportQuestion(questionId){const choices={'1':'wrong_answer','2':'unclear','3':'duplicate','4':'source','5':'other'};const selected=prompt('سبب البلاغ:\n1- الإجابة غير صحيحة\n2- السؤال غير واضح\n3- سؤال مكرر\n4- مشكلة في المصدر\n5- سبب آخر','1');if(!choices[selected])return;const message=prompt('اكتب ملاحظة مختصرة تساعد المراجع (اختياري):','')||'';try{const data=await questionTool('report',questionId,{reason:choices[selected],message});alert(data.message);}catch(e){alert(e.message);}}

async function chooseAnswerAndSubmit(id, text){
  const q = QUESTIONS[index];
  if (!q || !id || answers[q.id]?.submitted || answers[q.id]?.saving) return;
  const previous = answers[q.id] ? {...answers[q.id]} : null;
  answers[q.id] = {selected:id, selected_text:text, text:text, submitted:true, saving:true};
  saveLocal();
  render();

  const result = await saveServer();
  if (result && result.payment_required) {
    answers[q.id] = previous || {selected:id, selected_text:text, text:text, submitted:false};
    quotaLocked = true;
    saveLocal();
    render();
    return;
  }
  if (!result || result.ok === false) {
    answers[q.id] = previous || {selected:id, selected_text:text, text:text, submitted:false};
    if (answers[q.id]) answers[q.id].saving = false;
    saveLocal();
    alert((result && result.message) || 'تعذر حفظ الإجابة. حاول مرة أخرى.');
    render();
    return;
  }

  answers[q.id] = {selected:id, selected_text:text, text:text, submitted:true, saving:false};
  saveLocal();
  render();
}

async function submitAnswer(){
  const q = QUESTIONS[index];
  let a = answers[q.id] || {};

  if (q.english_match) {
    const pairs=englishMatchPairs(a);
    if(!englishMatchComplete(q,pairs)){ alert('أكمل جميع التوصيلات بين A و B أولاً.'); return; }
    a.english_match_pairs=pairs;
    a.text=englishMatchAnswerText(q,pairs);
  } else if (q.type === 'calculation') {
    if(!calculationComplete(q,a)){ alert('أكمل مراحل المسألة المطلوبة أولاً.'); return; }
    a.text=JSON.stringify({stages:calcState(a)});
  } else if (q.type === 'genetics') {
    if(!geneticsComplete(q,a)){ alert('أكمل جميع مراحل المسألة الوراثية أولاً.'); return; }
    a.text=JSON.stringify({stages:geneticsState(a)});
  } else if (q.type === 'match') {
    const state=matchState(a);
    const items=q.match_items||[];
    if(!items.length){ alert('لا توجد عناصر لهذا السؤال.'); return; }
    for(const item of items){
      const g=state[String(item.id)]||{};
      if(!String(g.type||'').trim()){ alert('اختر نوع المد لكل كلمة.'); return; }
      if(!String(g.reason||'').trim()){ alert('اكتب سبب نوع المد لكل كلمة.'); return; }
    }
    a.match=state;
    a.text=JSON.stringify({items:state});
  } else if (q.type === 'mcq' || (q.type === 'meaning' && q.answers && q.answers.length) || q.type === 'true_false') {
    // أسئلة الاختيار تُحفظ وتُصحح فور النقر على الخيار، ولا تستخدم زر التسليم.
    return;
  } else {
    const field = document.querySelector('#textAnswer');
    const text = field ? field.value.trim() : '';
    if (!text) {
      alert('اكتب إجابتك أولاً');
      return;
    }
    a.text = text;
  }

  const before = {...a};
  a.submitted = true;
  answers[q.id] = a;
  saveLocal();
  const result = await saveServer();
  if (result && result.payment_required) {
    answers[q.id] = {...before, submitted:false};
    quotaLocked = true;
    saveLocal();
    render();
    return;
  }
  if (result && result.ok === false) {
    answers[q.id] = {...before, submitted:false};
    saveLocal();
    alert(result.message || 'تعذر حفظ الإجابة. حاول مرة أخرى.');
    render();
    return;
  }
  render();
}

function move(delta){
  const target = index + delta;
  if (target < 0) return;
  if (target >= QUESTIONS.length) {
    index = QUESTIONS.length;
  } else {
    index = target;
  }
  saveLocal();
  saveServer();
  render();
}

function saveLocal(){
  localStorage.setItem('exam_' + CHAPTER_ID, JSON.stringify({index, answers}));
}

let contestToastTimer=null;
function showContestUpdate(c){
  if(!c || !Number(c.xp_gain||0)) return;
  const el=document.getElementById('contestXpToast'); if(!el) return;
  const gain=Number(c.xp_gain||0), before=Number(c.rank_before||0), after=Number(c.rank_after||0);
  const signed = gain > 0 ? `+${gain}` : `${gain}`;
  let headline=`${signed} نقطة — تحدي المليون`;
  let detail=gain > 0 ? 'تمت إضافة النقاط إلى رصيدك في ترتيب جميع الصفوف.' : 'تم خصم نقطتين بسبب إجابة خاطئة، لأن رصيدك كان أكثر من 100 نقطة.';
  if(before>0 && after>0 && after<before) detail=`تقدمت من المركز #${before} إلى #${after} 🎉`;
  else if(after===1) detail='أنت الآن في المركز الأول 🏆';
  else if(Number(c.points_to_next||0)>0) detail=`مركزك #${after} · تحتاج ${Number(c.points_to_next)} نقطة لتتجاوز المركز الأعلى.`;
  el.innerHTML=`<strong>${headline}</strong><small>${detail}</small>`;
  el.classList.add('show'); clearTimeout(contestToastTimer); contestToastTimer=setTimeout(()=>el.classList.remove('show'),3600);
}

function applyServerGrades(data){
  if(!data || !data.grades) return;
  Object.entries(data.grades).forEach(([qid,g])=>{
    const id=String(qid);
    answers[id]=answers[id]||{};
    answers[id].grade_status=g.status||'incorrect';
    answers[id].grade_score=Number(g.score||0);
    answers[id].grade_message=g.message||'';
    if(g.details) answers[id].grade_details=g.details;
    answers[id].is_correct=!!g.is_correct;
  });
  saveLocal();
}

async function saveServer(){
  try {
    const response = await fetch('../api/progress.php', {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({chapter_id:CHAPTER_ID, question_index:index, answers:answers, completed:(index >= QUESTIONS.length), csrf:CSRF})
    });
    const data = await response.json().catch(()=>({ok:false,message:'استجابة غير صالحة من الخادم'}));
    if (response.status === 402 || data.payment_required) quotaLocked = true;
    if(data && data.ok){ applyServerGrades(data); showContestUpdate(data.contest); }
    return data;
  } catch(e) {
    return {ok:false,message:'تعذر حفظ التقدم في الحساب. تحقق من الاتصال وحاول مرة أخرى.'};
  }
}


async function requestStatus(scope,id){
  try{
    const r=await fetch('../api/question-request.php?status=1&scope='+encodeURIComponent(scope)+'&id='+encodeURIComponent(id),{cache:'no-store'});
    return await r.json();
  }catch(e){ return {ok:false,complete:false,pending:false}; }
}
async function refreshRequestEligibility(){
  const unitState=document.getElementById('chapterRequestState');
  const unitBtn=document.getElementById('requestChapterBtn');
  if(unitBtn){
    const st=await requestStatus('chapter',CHAPTER_ID);
    if(st.complete){
      unitBtn.hidden=false;
      if(st.pending){unitBtn.disabled=true;unitBtn.textContent='تم إرسال طلب الموضوع';if(unitState)unitState.textContent='الطلب بانتظار الإدارة.';}
      else{unitBtn.disabled=false;unitBtn.textContent='طلب أسئلة جديدة لهذا الموضوع';if(unitState)unitState.textContent='';}
    }else{unitBtn.hidden=true;if(unitState)unitState.textContent='';}
  }
  const box=document.getElementById('subjectRequestAction');
  if(box){
    const st=await requestStatus('subject',SUBJECT_ID);
    if(st.complete){
      box.innerHTML=st.pending
        ? `<button type="button" class="secondary-btn" disabled>تم إرسال طلب للمادة</button><span class="request-state">بانتظار الإدارة.</span>`
        : `<button type="button" class="secondary-btn" onclick="requestNewQuestions('subject')">طلب أسئلة جديدة للمادة كاملة</button><span class="request-state"></span>`;
    }else box.innerHTML='';
  }
}
async function requestNewQuestions(scope){
  const fd=new FormData();fd.append('csrf',CSRF);fd.append('scope',scope);
  if(scope==='chapter')fd.append('chapter_id',String(CHAPTER_ID));else fd.append('subject_id',String(SUBJECT_ID));
  const buttons=document.querySelectorAll('.question-request-actions button');buttons.forEach(b=>b.disabled=true);
  try{
    const r=await fetch('../api/question-request.php',{method:'POST',body:fd,headers:{'Accept':'application/json'},cache:'no-store'});
    const d=await r.json().catch(()=>({ok:false,message:'استجابة غير صالحة من الخادم'}));
    if(!r.ok||!d.ok)throw new Error(d.message||'تعذر إرسال الطلب.');
    alert(d.message||'تم إرسال الطلب إلى الإدارة.');
    await refreshRequestEligibility();
  }catch(e){alert(e.message||'تعذر إرسال الطلب.');buttons.forEach(b=>b.disabled=false);}
}

render();
</script>
<script defer src="<?= BASE_URL ?>/assets/js/app.js?v=20260929-v586stories"></script>
</body>
</html>
