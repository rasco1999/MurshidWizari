<?php
/** Read-only quality triage. Does not alter student records or question status. */
declare(strict_types=1);
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE',true);
require_once __DIR__.'/../config/config.php';
require_admin_capability('questions');
$pdo=db();
$view=(string)($_GET['view']??'duplicates');
$allowed=['duplicates','conflicts','missing_explanations','missing_options','under_100'];
if(!in_array($view,$allowed,true))$view='duplicates';
$labels=[
 'duplicates'=>'أسئلة متكررة','conflicts'=>'إجابات مختلفة للنص نفسه',
 'missing_explanations'=>'شروح غير مكتملة','missing_options'=>'خيارات ناقصة','under_100'=>'مواضيع تحت 100 سؤال'
];
$live="q.status=1 AND q.is_active=1 AND q.moderation_status='approved'";
$rows=[];$error='';
try {
    switch($view){
    case 'duplicates':
        $stmt=$pdo->query("SELECT MIN(q.id) id,q.chapter_id,COUNT(*) n,LEFT(MIN(q.question_text),250) question_text,
          LEFT(MIN(q.correct_answer),190) correct_answer,MIN(c.name) chapter_name,MIN(s.name) subject_name
          FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
          WHERE $live GROUP BY q.chapter_id,SHA2(CONCAT(TRIM(q.question_text),CHAR(1),TRIM(q.correct_answer)),256)
          HAVING COUNT(*)>1 ORDER BY n DESC,id LIMIT 120");
        $rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];break;
    case 'conflicts':
        $stmt=$pdo->query("SELECT MIN(q.id) id,q.chapter_id,COUNT(*) n,LEFT(MIN(q.question_text),250) question_text,
          LEFT(MIN(q.correct_answer),190) correct_answer,MIN(c.name) chapter_name,MIN(s.name) subject_name
          FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
          WHERE $live GROUP BY q.chapter_id,SHA2(TRIM(q.question_text),256)
          HAVING COUNT(DISTINCT SHA2(TRIM(q.correct_answer),256))>1 ORDER BY n DESC,id LIMIT 120");
        $rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];break;
    case 'missing_explanations':
        $stmt=$pdo->query("SELECT q.id,q.chapter_id,1 n,LEFT(q.question_text,250) question_text,
           LEFT(q.correct_answer,190) correct_answer,c.name chapter_name,s.name subject_name
           FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
           WHERE $live AND (q.explanation IS NULL OR TRIM(q.explanation)='')
           ORDER BY q.id ASC LIMIT 120");
        $rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];break;
    case 'missing_options':
        $stmt=$pdo->query("SELECT q.id,q.chapter_id,1 n,LEFT(q.question_text,250) question_text,
           LEFT(q.correct_answer,190) correct_answer,c.name chapter_name,s.name subject_name
           FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
           LEFT JOIN (SELECT question_id,COUNT(*) opt_count FROM answers GROUP BY question_id) a ON a.question_id=q.id
           WHERE $live AND q.type IN ('mcq','true_false')
           AND GREATEST(
              (IF(TRIM(COALESCE(q.option_a,''))<>'',1,0)+IF(TRIM(COALESCE(q.option_b,''))<>'',1,0)+
               IF(TRIM(COALESCE(q.option_c,''))<>'',1,0)+IF(TRIM(COALESCE(q.option_d,''))<>'',1,0)),
              COALESCE(a.opt_count,0))<2 ORDER BY q.id LIMIT 120");
        $rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];break;
    case 'under_100':
        $stmt=$pdo->query("SELECT c.id,c.id chapter_id,COALESCE(COUNT(q.id),0) n,c.name chapter_name,
           s.name subject_name,c.name question_text,'' correct_answer
           FROM chapters c JOIN subjects s ON s.id=c.subject_id
           LEFT JOIN questions q ON q.chapter_id=c.id AND $live
           WHERE c.status=1 AND s.status=1
           GROUP BY c.id,c.name,s.name HAVING n<100 ORDER BY n,c.id LIMIT 120");
        $rows=$stmt->fetchAll(PDO::FETCH_ASSOC)?:[];break;
    }
}catch(Throwable $ex){error_log('question quality triage: '.$ex->getMessage());$error='تعذر إكمال التقرير. تحقق من بنية قاعدة البيانات.';}
?><!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>جودة الأسئلة | المرشد الوزاري</title>
<link rel="stylesheet" href="<?=e(BASE_URL)?>/assets/css/platform.css"><link rel="stylesheet" href="<?=e(BASE_URL)?>/assets/css/brand-v57.css">
<style>.qc{max-width:1130px;margin:20px auto;padding:12px}.qc-nav{display:flex;gap:8px;flex-wrap:wrap;margin:14px 0}.qc-nav a{padding:8px 12px;border-radius:10px;border:1px solid var(--border,#bbb);text-decoration:none;color:inherit}.qc-nav a.active{font-weight:800;background:var(--surface-2,#ddd)}.qc table{border-collapse:collapse;width:100%;font-size:14px}.qc th,.qc td{padding:11px;border-bottom:1px solid #9995;text-align:right;vertical-align:top}.qc small{opacity:.8}.qc .notice{margin:10px 0;padding:12px;border:1px solid #9995;border-radius:9px}.qc a{word-break:break-word}@media(max-width:700px){.qc table{display:block;overflow-x:auto}}</style></head>
<body class="page platform-admin" data-es-role="admin" data-es-base="..">
<?php platform_bar('admin');?><main class="qc"><h1>تدقيق جودة بنك الأسئلة</h1>
<p>فحص مباشر من قاعدة البيانات للفرز والمراجعة. <strong>لا يتم حذف أي سؤال أو تعديل نتائجه من هذه الصفحة.</strong></p>
<div class="qc-nav"><?php foreach($labels as $code=>$label):?><a class="<?=$code===$view?'active':''?>" href="?view=<?=e($code)?>"><?=e($label)?></a><?php endforeach;?></div>
<div class="notice">هذا جدول لأعلى 120 حالة بحسب نوع التقرير، لا يُعد إحصاءً لجميع الحالات. قد تتكرر الأسئلة بين سنوات امتحانية أو مصادر مختلفة، وتحتاج قراراً تحريرياً قبل الدمج.</div>
<?php if($error):?><div class="notice"><?=e($error)?></div><?php else:?>
<p>النتائج المعروضة: <strong><?=count($rows)?></strong></p>
<table><thead><tr><th>السؤال / الموضوع</th><th>الموقع</th><th>التكرار / العدد</th><th>الإجراء</th></tr></thead><tbody>
<?php foreach($rows as $row):?><tr><td><strong>#<?=(int)$row['id']?></strong> <?=e((string)$row['question_text'])?>
<?php if($view!=='under_100'):?><div><small>النموذج: <?=e((string)$row['correct_answer'])?></small></div><?php endif;?></td>
<td><?=e((string)$row['subject_name'])?> / <?=e((string)$row['chapter_name'])?></td>
<td><?=(int)$row['n']?></td><td><a href="questions.php?<?=($view==='under_100'?'chapter_id':'edit')?>=<?=(int)$row['id']?>">فتح في بنك الأسئلة</a></td></tr><?php endforeach; if(!$rows):?><tr><td colspan="4">لا توجد نتائج في هذا القسم.</td></tr><?php endif; ?></tbody></table>
<?php endif;?><p><a href="questions.php">العودة إلى بنك الأسئلة</a></p></main></body></html>
