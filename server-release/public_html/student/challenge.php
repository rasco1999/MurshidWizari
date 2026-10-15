<?php
declare(strict_types=1);
require_once __DIR__.'/../config/config.php';
require_once __DIR__.'/../includes/daily_challenge_v48.php';
require_once __DIR__.'/../includes/matching_widget_v53.php';
$u=require_student();$pdo=db();murshid_daily_v48_schema($pdo);$q=murshid_daily_v48_question($pdo,(int)$u['grade_id']);
$done=murshid_daily_v48_attempt($pdo,(int)$u['id']);$result=null;
if($_SERVER['REQUEST_METHOD']==='POST' && $q){
 if(!verify_csrf($_POST['csrf']??null)){$error='انتهت صلاحية النموذج؛ حدّث الصفحة.';}
 elseif($done){$error='أجبت عن تحدي اليوم مسبقاً.';}
 else{$result=murshid_daily_v48_submit($pdo,$u,$q,(string)($_POST['answer']??''));if(!$result['ok'])$error=$result['message'];else $done=['is_correct'=>$result['score'],'question_id'=>$q['id'],'given_answer'=>trim((string)$_POST['answer'])];}
}
$answerDetails=$done?murshid_daily_v48_answer_details($pdo,(int)$done['question_id'],(int)$u['grade_id']):null;
?><!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تحدي اليوم — المرشد الوزاري</title><script src="<?=BASE_URL?>/assets/js/theme-init.js"></script><link rel="stylesheet" href="<?=BASE_URL?>/assets/css/platform.css"><link rel="stylesheet" href="<?=BASE_URL?>/assets/css/brand-v57.css"><link rel="stylesheet" href="<?=BASE_URL?>/assets/css/matching-v53.css"><script defer src="<?=BASE_URL?>/assets/js/matching-v53.js"></script></head>
<body class="page cinema-study-skin platform-student" data-es-role="student" data-es-base="..">
<?php platform_bar('student');?><main class="container elite-shell"><section class="card challenge-card" style="max-width:840px;margin:2rem auto;padding:22px">
<h1>تحدي اليوم</h1><p>سؤال صعب واحد لكل 24 ساعة، يُتاح بعد مرور 24 ساعة من إجابتك السابقة. لا يستهلك الأسئلة المجانية ولا يُضاف إلى نقاطك.</p>
<?php if(!empty($error)):?><p class="support-alert bad"><?=e($error)?></p><?php endif;?>
<?php if($done):?>
<div class="support-alert" role="status"><?=((int)$done['is_correct']===1)?'أحسنت! إجابتك صحيحة.':'تم تسليم الإجابة وانتهى تحدي اليوم.'?></div>
<section style="padding:16px;margin:16px 0;border:1px solid var(--line, #cbd5e1);border-radius:14px">
  <?php if($answerDetails):?>
    <h2 style="margin:0 0 10px">الجواب الصحيح</h2>
    <p style="font-weight:800;white-space:pre-wrap"><?=e((string)$answerDetails['correct_answer'])?></p>
    <?php if(trim((string)($answerDetails['explanation']??''))!==''):?><p style="white-space:pre-wrap"><strong>التوضيح:</strong> <?=e((string)$answerDetails['explanation'])?></p><?php endif;?>
  <?php endif;?>
  <p class="muted" style="white-space:pre-wrap"><strong>إجابتك:</strong> <?=e((string)($done['given_answer']??''))?></p>
  <small>سيكون التحدي التالي متاحًا بعد مرور 24 ساعة على محاولتك.</small>
</section>
<?php elseif(!$q):?><p class="support-alert">لا يوجد سؤال متاح حالياً.</p>
<?php else:?><form method="post"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><p class="muted"><?=e($q['subject_name'])?> — <?=e($q['chapter_name'])?></p><h2><?=e(!empty($q['english_match'])?'صِل كل عنصر من القائمة A بما يناسبه من القائمة B.':public_question_display_text((string)$q['question_text']))?></h2>
<?php if(!empty($q['english_match'])):?><?php murshid_v53_matching_widget($q['_english_match'],'answer');?><?php elseif($q['options']):?><?php foreach($q['options'] as $option):?><label class="answer-option" style="display:block;margin:10px 0"><input type="radio" name="answer" value="<?=e((string)$option)?>" required> <?=e((string)$option)?></label><?php endforeach;?>
<?php else:?><textarea name="answer" required maxlength="2000" placeholder="اكتب إجابتك" rows="4"></textarea><?php endif;?><button class="primary-btn" type="submit">تسليم الإجابة</button></form><?php endif;?>
<a class="secondary-btn" href="<?=BASE_URL?>/" style="margin-top:16px">العودة للرئيسية</a></section></main><script defer src="<?=BASE_URL?>/assets/js/app.js?v=20261009-school-ads"></script></body></html>
