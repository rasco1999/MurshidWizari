<?php
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__.'/../config/config.php';
require_once __DIR__.'/../includes/school_ads.php';
require_once __DIR__.'/../includes/school_ad_apns.php';
$u=require_admin();$pdo=db();

$msg='';$err='';

function admin_parse_ad_datetime(string $value,string $label): DateTimeImmutable {
    $value=trim($value);
    $dt=DateTimeImmutable::createFromFormat('Y-m-d\TH:i',$value);
    $errors=DateTimeImmutable::getLastErrors();
    if(!$dt || ($errors && (($errors['warning_count']??0)>0 || ($errors['error_count']??0)>0)) || $dt->format('Y-m-d\TH:i')!==$value){
        throw new RuntimeException($label.' غير صحيح.');
    }
    return $dt;
}
function admin_ad_payload(): array {
    $school=trim((string)($_POST['school_name']??''));
    $text=trim((string)($_POST['ad_text']??''));
    if($school===''||mb_strlen($school)>180)throw new RuntimeException('اكتب اسم المدرسة بشكل صحيح.');
    if($text===''||mb_strlen($text)>500)throw new RuntimeException('اكتب نص الإعلان بشكل صحيح.');
    $start=admin_parse_ad_datetime((string)($_POST['start_at']??''),'وقت بداية الإعلان');
    $end=admin_parse_ad_datetime((string)($_POST['end_at']??''),'وقت نهاية الإعلان');
    if($end <= $start)throw new RuntimeException('وقت نهاية الإعلان يجب أن يكون بعد وقت البداية.');
    $days=school_ad_billable_days($start,$end);
    $price=$days*5000;
    $maxImpressions=max(0,min(10000000,(int)($_POST['max_impressions']??0)));
    return [
        'school'=>$school,'text'=>$text,'start'=>$start,'end'=>$end,'days'=>$days,'price'=>$price,'max_impressions'=>$maxImpressions,
        'start_sql'=>$start->format('Y-m-d H:i:s'),'end_sql'=>$end->format('Y-m-d H:i:s'),
        'start_date'=>$start->format('Y-m-d'),'end_date'=>$end->format('Y-m-d'),
    ];
}

if($_SERVER['REQUEST_METHOD']==='POST'){
    require_post_csrf();
    $action=(string)($_POST['action']??'create');
    try{
        if($action==='create'){
            $p=admin_ad_payload();
            $pdo->prepare("INSERT INTO school_ads(school_name,ad_text,image_path,start_date,end_date,start_at,end_at,days,daily_price,total_price,is_enabled,max_impressions,impressions,created_by) VALUES(?,?,NULL,?,?,?,?,?,5000,?,1,?,0,?)")
                ->execute([$p['school'],$p['text'],$p['start_date'],$p['end_date'],$p['start_sql'],$p['end_sql'],$p['days'],$p['price'],$p['max_impressions'],(int)$u['id']]);
            $newAdId=(int)$pdo->lastInsertId();
            murshid_school_push_enqueue($pdo,$newAdId);
            audit_log('إضافة إعلان مدرسة','school_ad',(string)$pdo->lastInsertId());
            $msg='تم إنشاء الإعلان. المدة المحتسبة: '.$p['days'].' يوم/أيام، الإجمالي: '.number_format($p['price']).' د.ع';
        }elseif($action==='update'){
            $id=(int)($_POST['id']??0);if($id<1)throw new RuntimeException('الإعلان غير موجود.');
            $p=admin_ad_payload();
            $wasEnabledQuery=$pdo->prepare('SELECT is_enabled FROM school_ads WHERE id=?');
            $wasEnabledQuery->execute([$id]);
            $wasEnabled=(int)$wasEnabledQuery->fetchColumn();
            $enabled=isset($_POST['is_enabled'])?1:0;
            $pdo->prepare("UPDATE school_ads SET school_name=?,ad_text=?,start_date=?,end_date=?,start_at=?,end_at=?,days=?,daily_price=5000,total_price=?,is_enabled=?,max_impressions=?,updated_at=NOW() WHERE id=?")
                ->execute([$p['school'],$p['text'],$p['start_date'],$p['end_date'],$p['start_sql'],$p['end_sql'],$p['days'],$p['price'],$enabled,$p['max_impressions'],$id]);
            if($enabled===1 && $wasEnabled!==1)murshid_school_push_enqueue($pdo,$id);
            audit_log('تعديل إعلان مدرسة','school_ad',(string)$id);$msg='تم حفظ تعديلات الإعلان.';
        }elseif($action==='toggle'){
            $id=(int)($_POST['id']??0);$pdo->prepare("UPDATE school_ads SET is_enabled=IF(is_enabled=1,0,1),updated_at=NOW() WHERE id=?")->execute([$id]);
            murshid_school_push_enqueue($pdo,$id); // disabled advertisements are ignored
            audit_log('تبديل حالة إعلان مدرسة','school_ad',(string)$id);$msg='تم تحديث حالة الإعلان.';
        }elseif($action==='reset_impressions'){
            $id=(int)($_POST['id']??0);$pdo->prepare("UPDATE school_ads SET impressions=0,updated_at=NOW() WHERE id=?")->execute([$id]);
            audit_log('تصفير ظهور إعلان مدرسة','school_ad',(string)$id);$msg='تم تصفير عداد الظهور.';
        }elseif($action==='delete'){
            $id=(int)($_POST['id']??0);$pdo->prepare("DELETE FROM school_ads WHERE id=?")->execute([$id]);
            audit_log('حذف إعلان مدرسة','school_ad',(string)$id);$msg='تم حذف الإعلان.';
        }
    }catch(Throwable $e){$err=$e->getMessage();}
}
$rows=[];try{$rows=$pdo->query("SELECT * FROM school_ads ORDER BY id DESC LIMIT 200")->fetchAll()?:[];}catch(Throwable $e){$err=$err?:$e->getMessage();}
$now=date('Y-m-d H:i:s');$defaultStart=date('Y-m-d\TH:i');$defaultEnd=date('Y-m-d\TH:i',time()+86400);
?><!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>إعلانات المدارس | الإدارة</title><script src="<?=BASE_URL?>/assets/js/theme-init.js?v=20260928-v56"></script><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/platform.css?v=20260929-v584contrast"><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/brand-v57.css?v=20260929-v5811responsive"><link rel="icon" href="<?=BASE_URL?>/assets/img/site-icon-512.png?v=20260928-v56">
</head>
<body class="page admin-theme admin-responsive-page platform-admin" data-es-role="admin" data-es-base="..">
<?php platform_bar('admin'); ?><main class="container">
<div class="admin-page-head"><div><span class="eyebrow">5,000 د.ع لكل 24 ساعة أو جزء منها</span><h1>إعلانات المدارس</h1><p>حدد وقت البداية والنهاية بالدقيقة. الإعلان يظهر فقط في الرئيسية للطالب، ولا توجد حاجة لرفع صورة.</p></div></div>
<?php if($msg):?><div class="support-alert"><?=e($msg)?></div><?php endif;?><?php if($err):?><div class="support-alert bad"><?=e($err)?></div><?php endif;?>
<section class="card school-ad-admin-create"><div class="section-heading"><div><h2>إعلان جديد</h2><p>مثال: تعلن مدرسة الهدى الأهلية عن فتح التسجيل للعام الدراسي الجديد.</p></div><span class="results-count">مدارس فقط</span></div>
<form method="post" class="school-ad-admin-form ad-time-form"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="action" value="create">
<label>اسم المدرسة<input name="school_name" maxlength="180" required placeholder="مدرسة ..."></label>
<label>نص الإعلان<textarea name="ad_text" maxlength="500" required placeholder="تعلن مدرسة ..."></textarea></label>
<div class="school-ad-form-grid"><label>بداية الإعلان<input type="datetime-local" name="start_at" value="<?=e($defaultStart)?>" required></label><label>نهاية الإعلان<input type="datetime-local" name="end_at" value="<?=e($defaultEnd)?>" required></label></div><label>حد الظهور (اختياري)<input type="number" name="max_impressions" min="0" max="10000000" value="0" placeholder="0 = بدون حد"><small>يُحسب الظهور مرة واحدة تقريبًا لكل جلسة طالب، لتقليل التكرار المزعج.</small></label>
<div class="school-ad-price-box"><span>السعر اليومي <b>5,000 د.ع</b></span><strong data-ad-total>5,000 د.ع</strong></div><button class="primary-btn" type="submit"><?=ui_icon('plus')?> إنشاء الإعلان</button></form></section>

<section class="school-ad-admin-list"><div class="section-heading"><div><h2>الإعلانات الحالية والسابقة</h2></div><span class="results-count"><?=count($rows)?> إعلان</span></div>
<?php foreach($rows as $i=>$ad): $adState=school_ad_status($ad,$now);$live=$adState['live'];$startAt=(string)($ad['start_at']?:($ad['start_date'].' 00:00:00'));$endAt=(string)($ad['end_at']?:($ad['end_date'].' 23:59:59'));$startInput=date('Y-m-d\TH:i',strtotime($startAt));$endInput=date('Y-m-d\TH:i',strtotime($endAt));?>
<article class="card school-ad-admin-card school-ad-admin-card-noimage"><div class="school-ad-admin-copy"><span class="status-pill <?=$live?'done':'waiting'?>"><?=e($adState['label'])?></span><h3>إعلان رقم <?=number_format($i+1)?> — <?=e($ad['school_name'])?></h3><p><?=e($ad['ad_text'])?></p><small><?=e(date('Y-m-d H:i',strtotime($startAt)))?> ← <?=e(date('Y-m-d H:i',strtotime($endAt)))?> · <?=number_format((int)$ad['days'])?> يوم محتسب · <?=number_format((int)$ad['total_price'])?> د.ع · <?=number_format((int)($ad['impressions']??0))?> ظهور<?=((int)($ad['max_impressions']??0)>0?' / '.number_format((int)$ad['max_impressions']):'')?></small><small class="school-ad-status-reason"><?=e($adState['reason'])?></small></div>
<div class="actions"><details class="school-ad-edit-details"><summary class="primary-btn">تعديل</summary><form method="post" class="school-ad-admin-form ad-time-form school-ad-inline-edit"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="action" value="update"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><label>اسم المدرسة<input name="school_name" value="<?=e($ad['school_name'])?>" maxlength="180" required></label><label>نص الإعلان<textarea name="ad_text" maxlength="500" required><?=e($ad['ad_text'])?></textarea></label><div class="school-ad-form-grid"><label>البداية<input type="datetime-local" name="start_at" value="<?=e($startInput)?>" required></label><label>النهاية<input type="datetime-local" name="end_at" value="<?=e($endInput)?>" required></label></div><label>حد الظهور<input type="number" name="max_impressions" min="0" max="10000000" value="<?=(int)($ad['max_impressions']??0)?>"><small>0 = بدون حد · الظهور الحالي <?=number_format((int)($ad['impressions']??0))?></small></label><label class="school-ad-enabled-check"><input type="checkbox" name="is_enabled" value="1" <?=(int)$ad['is_enabled']?'checked':''?>> الإعلان مفعّل</label><div class="school-ad-price-box"><span>السعر المحسوب</span><strong data-ad-total><?=number_format((int)$ad['total_price'])?> د.ع</strong></div><button class="primary-btn" type="submit">حفظ التعديل</button></form></details>
<form method="post"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><button class="secondary-btn" type="submit" name="action" value="toggle"><?=(int)$ad['is_enabled']?'إيقاف':'تشغيل'?></button></form><form method="post"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><button class="secondary-btn" type="submit" name="action" value="reset_impressions">تصفير الظهور</button></form><form method="post" onsubmit="return confirm('حذف الإعلان نهائياً؟')"><input type="hidden" name="csrf" value="<?=e(csrf_token())?>"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><button class="danger-btn" type="submit" name="action" value="delete">حذف</button></form></div></article>
<?php endforeach;?><?php if(!$rows):?><div class="empty-state card"><strong>لا توجد إعلانات بعد</strong><span>أنشئ أول إعلان مدرسة من النموذج أعلاه.</span></div><?php endif;?></section>
</main><script>
function bindAdPrice(form){const s=form.querySelector('[name="start_at"]'),e=form.querySelector('[name="end_at"]'),out=form.querySelector('[data-ad-total]');if(!s||!e||!out)return;function calc(){const a=new Date(s.value),b=new Date(e.value);if(!s.value||!e.value||isNaN(a)||isNaN(b)||b<=a){out.textContent='—';return;}const days=Math.max(1,Math.ceil((b-a)/86400000));out.textContent=(days*5000).toLocaleString('en-US')+' د.ع';}s.addEventListener('input',calc);e.addEventListener('input',calc);calc();}document.querySelectorAll('.ad-time-form').forEach(bindAdPrice);
</script><script defer src="<?=BASE_URL?>/assets/js/app.js?v=20260929-v586stories"></script></body></html>
