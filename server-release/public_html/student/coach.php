<?php
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
require_once __DIR__ . '/../includes/integrations.php';
require_once __DIR__ . '/../includes/smart_coach.php';
$u = require_student();
$pdo = db();
try { $S = coach_stats($pdo, (int)$u['id'], (int)$u['grade_id']); }
catch (Throwable $e) { error_log('coach page: ' . $e->getMessage()); $S = ['due'=>0,'answered'=>0,'accuracy'=>0,'readiness'=>0,'mastered'=>0,'streak'=>0,'weak'=>[]]; }
$aiOn = !empty(integration('ai', 'enabled')) && !empty(integration('ai', 'api_key'));
$v = '20261007-coach';
?><!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>المدرب الذكي</title>
<script src="<?=BASE_URL?>/assets/js/theme-init.js?v=20260928-v56"></script>
<link rel="stylesheet" href="<?=BASE_URL?>/assets/css/platform.css?v=20260929-v584contrast">
<link rel="stylesheet" href="<?=BASE_URL?>/assets/css/brand-v57.css?v=20260929-v5811responsive">
<link rel="stylesheet" href="<?=BASE_URL?>/assets/css/coach.css?v=<?=$v?>">
<link rel="icon" type="image/png" sizes="512x512" href="<?=BASE_URL?>/assets/img/site-icon-512.png?v=20260928-v56"><link rel="stylesheet" href="<?=BASE_URL?>/assets/css/matching-v53.css"><script defer src="<?=BASE_URL?>/assets/js/matching-v53.js"></script></head>
<body class="page cinema-study-skin platform-student" data-es-role="student" data-es-base=".." data-es-name="<?=e($u['full_name'])?>">
<?php platform_bar('student'); ?>
<main class="container coach" id="coachApp" data-csrf="<?=e(csrf_token())?>" data-base="<?=e(BASE_URL)?>" data-ai="<?=$aiOn?'1':'0'?>">

<section class="card coach-hero">
  <div class="coach-ring" style="--p:<?=(int)$S['readiness']?>"><b><?=(int)$S['readiness']?>%</b><small>الجاهزية</small></div>
  <div class="coach-hero-text">
    <h1 class="premium-title">المدرب الذكي</h1>
    <p class="muted">يراجع لك الأسئلة في الوقت الذي توشك فيه على نسيانها، ويدلّك على أضعف مواضيعك.</p>
    <div class="coach-stats">
      <span><b><?=(int)$S['streak']?></b> يوم متتالٍ</span>
      <span><b><?=(int)$S['accuracy']?>%</b> دقة</span>
      <span><b><?=(int)$S['mastered']?></b> سؤال متقن</span>
    </div>
  </div>
</section>

<section class="card coach-today">
  <div>
    <h2>مراجعة اليوم</h2>
    <p class="muted" id="dueLine"><?= $S['due'] > 0 ? 'لديك '.(int)$S['due'].' سؤالاً مستحق المراجعة الآن.' : ($S['answered'] ? 'أحسنت! لا مراجعات مستحقة الآن.' : 'أجب عن بعض الأسئلة أولاً لنبدأ بناء جدول مراجعتك.') ?></p>
  </div>
  <button class="primary-btn" id="startReview" <?= $S['due'] > 0 ? '' : 'disabled' ?>>ابدأ المراجعة</button>
</section>

<section class="card coach-session" id="session" hidden>
  <div class="coach-progress"><i id="sessBar"></i></div>
  <div class="muted" id="sessMeta"></div>
  <h2 id="sessQ" class="coach-q"></h2>
  <div class="coach-timer" id="sessTimer" aria-live="off"></div>
  <div id="sessOpts" class="coach-opts"></div>
  <div id="sessFeedback" class="coach-feedback" hidden></div>
  <div class="coach-actions">
    <?php if ($aiOn): ?><button class="secondary-btn" id="askAi" hidden>اشرح لي بالتفصيل</button><?php endif; ?>
    <button class="primary-btn" id="nextQ" hidden>التالي</button>
  </div>
  <div id="aiBox" class="coach-ai" hidden></div>
</section>

<section class="card coach-done" id="done" hidden>
  <h2>انتهت الجلسة</h2>
  <p id="doneLine" class="muted"></p>
  <a class="secondary-btn" href="coach.php">تحديث الصفحة</a>
</section>

<section class="card">
  <h2>أضعف مواضيعك</h2>
  <?php if (!$S['weak']): ?><p class="muted">لا توجد بيانات كافية بعد.</p><?php endif; ?>
  <?php foreach ($S['weak'] as $w): $sc = (int)$w['score']; ?>
  <div class="smart-row">
    <div class="smart-main">
      <b><?=e($w['subject_name'])?> — <?=e(public_topic_name((string)$w['subject_name'], (string)$w['chapter_name']))?></b>
      <span><?=(int)$w['wrong']?> أخطاء من <?=(int)$w['total']?> إجابة</span>
      <div class="smart-bar"><i style="width:<?=$sc?>%"></i></div>
    </div>
    <a class="secondary-btn" href="exam.php?chapter_id=<?=(int)$w['chapter_id']?>">تدرّب</a>
  </div>
  <?php endforeach; ?>
</section>
</main>
<script defer src="<?=BASE_URL?>/assets/js/app.js?v=20260929-v586stories"></script>
<script defer src="<?=BASE_URL?>/assets/js/coach.js?v=<?=$v?>"></script>
</body></html>
