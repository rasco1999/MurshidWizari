<?php
declare(strict_types=1);

// Mobile-first administration for the separate iPhone manager app.
// Put at public_html/admin/mobile-console.php.
// Deliberately do not call require_admin(): the existing site's page-name
// allowlist predates this new file. Apply the same role/2FA checks below,
// then check the existing per-capability permissions explicitly.
require_once __DIR__ . '/../config/config.php';
header('Cache-Control: no-store, no-cache, must-revalidate, private');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

$admin = current_user();
if (!$admin || ($admin['role'] ?? '') !== 'admin' || ($admin['status'] ?? '') !== 'active') {
    header('Location: /admin/index.php', true, 302);
    exit;
}
if (!empty($_SESSION['admin_2fa_pending'])) {
    header('Location: /admin/verify-2fa.php', true, 302);
    exit;
}
$accessFile = __DIR__ . '/../includes/platform_tools.php';
if (!is_file($accessFile)) {
    http_response_code(503);
    exit('ملف صلاحيات الإدارة غير متوفر على الخادم.');
}
require_once $accessFile;
$pdo = db();
$adminId = (int)$admin['id'];
$canCodes = function_exists('admin_can') && admin_can($pdo, $adminId, 'codes');
$canAds = function_exists('admin_can') && admin_can($pdo, $adminId, 'school_ads');
if (!$canCodes && !$canAds) {
    http_response_code(403);
    exit('هذا الحساب لا يملك صلاحية إدارة الأكواد أو الإعلانات.');
}
$section = (string)($_GET['section'] ?? ($canCodes ? 'codes' : 'ads'));
if (!in_array($section, ['codes', 'ads'], true)) $section = $canCodes ? 'codes' : 'ads';
if (($section === 'codes' && !$canCodes) || ($section === 'ads' && !$canAds)) {
    http_response_code(403);
    exit('ليست لديك صلاحية هذه الصفحة.');
}

$message = '';
$error = '';
$issuedCodes = [];

function admin_console_date(string $date): bool {
    $parsed = DateTimeImmutable::createFromFormat('!Y-m-d', $date);
    return $parsed !== false && $parsed->format('Y-m-d') === $date;
}
function admin_console_ad_data(array $data): array {
    $school = trim((string)($data['school_name'] ?? ''));
    $body = trim((string)($data['ad_text'] ?? ''));
    $from = trim((string)($data['start_date'] ?? ''));
    $to = trim((string)($data['end_date'] ?? ''));
    $image = trim((string)($data['image_path'] ?? ''));
    if ($school === '' || mb_strlen($school) > 180 || $body === '' || mb_strlen($body) > 500) {
        throw new InvalidArgumentException('اسم المدرسة ونص الإعلان مطلوبان ضمن الحدود المسموحة.');
    }
    if (!admin_console_date($from) || !admin_console_date($to) || $to < $from) {
        throw new InvalidArgumentException('تواريخ الإعلان غير صحيحة.');
    }
    $days = (int)(new DateTimeImmutable($from))->diff(new DateTimeImmutable($to))->days + 1;
    if ($days < 1 || $days > 366) throw new InvalidArgumentException('مدة الإعلان من يوم إلى 366 يوماً.');
    if ($image !== '' && (strlen($image) > 255 || !preg_match('~^/(?:[A-Za-z0-9_./-]+)$~D', $image) || str_contains($image, '..'))) {
        throw new InvalidArgumentException('مسار الصورة يجب أن يكون مساراً محلياً آمناً يبدأ بـ /.');
    }
    return [$school, $body, $from, $to, $days, 5000 * $days, $image === '' ? null : $image];
}

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    if (!verify_csrf($_POST['csrf'] ?? null)) {
        $error = 'انتهت صلاحية الصفحة. حدّثها ثم أعد المحاولة.';
    } elseif (!rate_limit('admin_mobile_console_mutation', 90, 3600)) {
        $error = 'وصلت إلى حد العمليات المسموح به مؤقتاً. أعد المحاولة لاحقاً.';
    } else {
        $action = (string)($_POST['action'] ?? '');
        try {
            if ($action === 'issue_codes' && $canCodes) {
                $months = filter_var($_POST['months'] ?? null, FILTER_VALIDATE_INT);
                $count = filter_var($_POST['count'] ?? null, FILTER_VALIDATE_INT);
                $batch = trim((string)($_POST['batch'] ?? 'admin'));
                $validUntil = trim((string)($_POST['valid_until'] ?? ''));
                if (!in_array($months, [1, 3], true) || $count === false || $count < 1 || $count > 50 ||
                    strlen($batch) > 80 || !preg_match('/^[A-Za-z0-9_-]{1,80}$/D', $batch) ||
                    ($validUntil !== '' && !admin_console_date($validUntil))) {
                    throw new InvalidArgumentException('اختر شهراً أو 3 أشهر، وعدد الأكواد من 1 إلى 50، وبيانات دفعة صالحة.');
                }
                $expires = $validUntil === '' ? null : $validUntil . ' 23:59:59';
                $pdo->beginTransaction();
                $insert = $pdo->prepare("INSERT INTO subscription_access_codes (code_hash,months,status,batch_label,valid_until)
                                          VALUES (?,?,'available',?,?)");
                for ($i = 0; $i < $count; $i++) {
                    $hex = strtoupper(bin2hex(random_bytes(12)));
                    $plain = 'MW-' . implode('-', str_split($hex, 4));
                    $insert->execute([hash('sha256', 'MW' . $hex), $months, $batch, $expires]);
                    $issuedCodes[] = $plain;
                }
                $pdo->commit();
                $message = 'أُصدرت ' . $count . ' أكواد لمدة ' . $months . ' شهر. انسخها الآن؛ لن تُعرض مجدداً.';
            } elseif ($action === 'revoke_code' && $canCodes) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if (!$id || $id < 1) throw new InvalidArgumentException('معرّف الكود غير صحيح.');
                $q = $pdo->prepare("UPDATE subscription_access_codes SET status='revoked' WHERE id=? AND status='available'");
                $q->execute([$id]);
                if ($q->rowCount() !== 1) throw new InvalidArgumentException('يمكن إلغاء الأكواد المتاحة فقط. لا يمكن سحب اشتراك فُعّل سابقاً من هنا.');
                $message = 'تم إلغاء الكود. لم يعد صالحاً للاستخدام.';
            } elseif ($action === 'delete_code' && $canCodes) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if (!$id || ($_POST['confirm'] ?? '') !== 'DELETE') throw new InvalidArgumentException('التأكيد مطلوب لحذف الكود.');
                $q = $pdo->prepare("DELETE FROM subscription_access_codes WHERE id=? AND status='revoked'");
                $q->execute([$id]);
                if ($q->rowCount() !== 1) throw new InvalidArgumentException('يجب إلغاء الكود أولاً؛ لا يُحذف الكود المستخدم حفاظاً على سجل الاشتراكات.');
                $message = 'حُذف سجل الكود الملغى نهائياً.';
            } elseif ($action === 'edit_code' && $canCodes) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                $batch = trim((string)($_POST['batch'] ?? ''));
                $validUntil = trim((string)($_POST['valid_until'] ?? ''));
                if (!$id || !preg_match('/^[A-Za-z0-9_-]{1,80}$/D', $batch) ||
                    ($validUntil !== '' && !admin_console_date($validUntil))) {
                    throw new InvalidArgumentException('بيانات تعديل الكود غير صحيحة.');
                }
                $q = $pdo->prepare("UPDATE subscription_access_codes SET batch_label=?,valid_until=?
                                     WHERE id=? AND status='available'");
                $q->execute([$batch, $validUntil === '' ? null : $validUntil . ' 23:59:59', $id]);
                $message = 'حُفظت بيانات الكود المتاح. لا يمكن تغيير مدة الكود بعد إصداره.';
            } elseif ($action === 'create_ad' && $canAds) {
                [$school,$body,$from,$to,$days,$total,$image] = admin_console_ad_data($_POST);
                $q = $pdo->prepare("INSERT INTO school_ads
                   (school_name,ad_text,image_path,start_date,end_date,days,daily_price,total_price,is_enabled,created_by)
                   VALUES (?,?,?,?,?,?,5000,?,1,?)");
                $q->execute([$school,$body,$image,$from,$to,$days,$total,$adminId]);
                $message = 'تم إنشاء إعلان المدرسة وربطه بعرض الإعلانات في المنصة.';
            } elseif ($action === 'edit_ad' && $canAds) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if (!$id || $id < 1) throw new InvalidArgumentException('معرّف الإعلان غير صحيح.');
                [$school,$body,$from,$to,$days,$total,$image] = admin_console_ad_data($_POST);
                $q = $pdo->prepare("UPDATE school_ads SET school_name=?,ad_text=?,image_path=?,start_date=?,end_date=?,
                   days=?,daily_price=5000,total_price=? WHERE id=?");
                $q->execute([$school,$body,$image,$from,$to,$days,$total,$id]);
                $message = 'تم تعديل الإعلان.';
            } elseif ($action === 'toggle_ad' && $canAds) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if (!$id || $id < 1) throw new InvalidArgumentException('معرّف الإعلان غير صحيح.');
                $q = $pdo->prepare('UPDATE school_ads SET is_enabled=1-is_enabled WHERE id=?');
                $q->execute([$id]);
                if ($q->rowCount() !== 1) throw new InvalidArgumentException('الإعلان غير موجود.');
                $message = 'تم تغيير حالة الإعلان.';
            } elseif ($action === 'delete_ad' && $canAds) {
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if (!$id || ($_POST['confirm'] ?? '') !== 'DELETE') throw new InvalidArgumentException('التأكيد مطلوب لحذف الإعلان.');
                $q = $pdo->prepare('DELETE FROM school_ads WHERE id=?');
                $q->execute([$id]);
                if ($q->rowCount() !== 1) throw new InvalidArgumentException('الإعلان غير موجود.');
                $message = 'حُذف الإعلان نهائياً من قاعدة البيانات.';
            } else {
                http_response_code(403);
                throw new InvalidArgumentException('العملية غير متاحة لهذه الصلاحية.');
            }
            error_log(sprintf('admin mobile console: admin=%d action=%s', $adminId, preg_replace('/[^a-z_]/', '', $action)));
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            if ($e instanceof InvalidArgumentException) $error = $e->getMessage();
            else {
                error_log('admin mobile console failed: ' . $e->getMessage());
                $error = 'تعذّر حفظ العملية. تأكد من تركيب جداول الإدارة وقاعدة البيانات.';
            }
            $issuedCodes = [];
        }
    }
}

$codes = [];
$ads = [];
try {
    if ($canCodes && $section === 'codes') {
        $codes = $pdo->query('SELECT id,months,status,batch_label,valid_until,redeemed_by,redeemed_at,created_at
                              FROM subscription_access_codes ORDER BY id DESC LIMIT 100')->fetchAll(PDO::FETCH_ASSOC);
    }
    if ($canAds && $section === 'ads') {
        $ads = $pdo->query('SELECT id,school_name,ad_text,image_path,start_date,end_date,days,total_price,
                                  is_enabled,impressions FROM school_ads ORDER BY id DESC LIMIT 100')->fetchAll(PDO::FETCH_ASSOC);
    }
} catch (Throwable $e) {
    error_log('admin mobile console list failed: ' . $e->getMessage());
    $error = 'لم تُجهّز جداول هذه الخدمة بعد. نفّذ ملف SQL للأكواد وتأكد من وجود جدول إعلانات المدارس.';
}
$csrfValue = csrf_token();
$path = '/admin/mobile-console.php';
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>الإدارة — منصة المرشد الوزاري</title>
  <style>
    :root{color-scheme:dark;--navy:#091b35;--panel:#112743;--panel2:#173351;--gold:#f4cb77;--ink:#f3f7ff;--muted:#bbcce0;--line:#34506c;--red:#ffb1a9;--green:#abedc8}
    *{box-sizing:border-box}body{margin:0;background:linear-gradient(145deg,#08162a,#102947 58%,#081c34);color:var(--ink);font-family:-apple-system,BlinkMacSystemFont,'Tajawal','Segoe UI',sans-serif;font-size:15px}
    main{max-width:900px;margin:auto;padding:20px 16px 90px}a{color:var(--gold)}a,button{-webkit-tap-highlight-color:transparent}
    header{padding:22px 20px;border:1px solid #45617d;border-radius:24px;background:linear-gradient(135deg,#243e60,#0e2340);box-shadow:0 12px 36px #030b1855}
    h1{font-size:26px;margin:0 0 7px;color:var(--gold)}h2{font-size:21px;margin:0 0 12px}h3{font-size:16px;margin:0 0 8px}p{line-height:1.7}
    .sub{color:var(--muted);font-size:13px}.nav{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.nav a{border:1px solid var(--line);text-decoration:none;background:var(--panel);padding:10px 14px;border-radius:12px;color:var(--ink);font-weight:700}.nav a.active{background:var(--gold);border-color:var(--gold);color:#0e2441}
    .panel{border:1px solid var(--line);border-radius:20px;padding:18px;margin:14px 0;background:var(--panel);box-shadow:0 6px 20px #02081433}
    .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px}.field{display:grid;gap:6px;margin-bottom:12px}.field label{font-size:13px;color:var(--muted)}input,select,textarea{background:#071a31;border:1px solid #60728c;border-radius:12px;padding:12px;color:white;font:inherit;width:100%;min-height:44px}textarea{min-height:90px;resize:vertical}input:focus,textarea:focus,select:focus{outline:2px solid var(--gold);outline-offset:1px}
    button{border:0;border-radius:12px;background:var(--gold);color:#0c2441;font-weight:800;font-size:15px;padding:11px 16px;min-height:43px;cursor:pointer}button.secondary{background:#344f6b;color:white}button.warn{background:#85433f;color:white}button.danger{background:#c1504a;color:white}
    .note{border-radius:12px;padding:12px 14px;margin:10px 0}.ok{border:1px solid #4a966f;background:#174632;color:var(--green)}.err{border:1px solid #9c5b50;background:#542b2a;color:#ffd1ca}
    .item{border:1px solid #3a5873;padding:14px;border-radius:15px;margin:10px 0;background:#0d233c}.item-head{display:flex;justify-content:space-between;align-items:start;gap:12px;flex-wrap:wrap}.badge{display:inline-block;border-radius:999px;padding:5px 9px;background:#405a73;font-size:12px}.badge.live{background:#216545;color:#d2ffdf}.actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.actions form{display:inline-flex}
    details{margin-top:12px;border-top:1px solid #32516c;padding-top:10px}summary{cursor:pointer;color:var(--gold);font-weight:700}pre{direction:ltr;text-align:left;white-space:pre-wrap;word-wrap:break-word;background:#051528;border:1px solid #426183;border-radius:10px;padding:14px;font-size:14px;line-height:1.8}
    small{color:var(--muted)}.empty{padding:30px 10px;text-align:center;color:var(--muted)}
    @media(max-width:540px){.grid{grid-template-columns:1fr}h1{font-size:23px}.panel{padding:14px}main{padding:12px 12px 100px}}
  </style>
</head>
<body><main>
  <header>
    <h1>الإدارة <span aria-hidden="true">✦</span></h1>
    <div>منصة المرشد الوزاري</div>
    <p class="sub">مرحباً <?=e((string)$admin['full_name'])?> — التحكم مرتبط بصلاحيات حسابك على الموقع، ولا يُمنح أي صلاحية بسبب تثبيت التطبيق وحده.</p>
  </header>
  <nav class="nav" aria-label="إدارة المنصة">
    <?php if ($canCodes): ?><a class="<?=$section==='codes'?'active':''?>" href="<?=$path?>?section=codes">أكواد الاشتراك</a><?php endif;?>
    <?php if ($canAds): ?><a class="<?=$section==='ads'?'active':''?>" href="<?=$path?>?section=ads">إعلانات المدارس</a><?php endif;?>
    <a href="/admin/dashboard.php">لوحة الموقع الكاملة</a>
  </nav>
  <?php if ($message): ?><div class="note ok" role="status"><?=e($message)?></div><?php endif;?>
  <?php if ($error): ?><div class="note err" role="alert"><?=e($error)?></div><?php endif;?>

  <?php if ($section === 'codes' && $canCodes): ?>
    <section class="panel">
      <h2>إنشاء أكواد التفعيل</h2>
      <p class="sub">الأكواد الحقيقية مدتها شهر أو 3 أشهر فقط. كل كود يُستخدم مرة واحدة، وتُضاف مدته إلى اشتراك الطالب الساري.</p>
      <form method="post" autocomplete="off">
        <input type="hidden" name="csrf" value="<?=e($csrfValue)?>">
        <input type="hidden" name="action" value="issue_codes">
        <div class="grid">
          <div class="field"><label for="months">مدة الاشتراك</label><select id="months" name="months" required><option value="1">شهر واحد</option><option value="3">3 أشهر</option></select></div>
          <div class="field"><label for="count">عدد الأكواد</label><input id="count" name="count" type="number" min="1" max="50" value="1" required></div>
          <div class="field"><label for="batch">اسم المجموعة</label><input id="batch" name="batch" value="admin" pattern="[A-Za-z0-9_-]+" maxlength="80" required></div>
          <div class="field"><label for="valid">آخر يوم لاستخدام الكود (اختياري)</label><input id="valid" name="valid_until" type="date"></div>
        </div>
        <button type="submit">+ إنشاء أكواد حقيقية</button>
      </form>
      <?php if ($issuedCodes): ?>
        <div class="note ok">
          <strong>انسخ هذه الأكواد الآن. لن نستطيع استرجاع نصوصها لاحقاً.</strong>
          <pre id="new-codes"><?=e(implode("\n",$issuedCodes))?></pre>
          <button type="button" onclick="navigator.clipboard.writeText(document.getElementById('new-codes').innerText)">نسخ جميع الأكواد</button>
        </div>
      <?php endif;?>
    </section>
    <section class="panel"><h2>الأكواد الصادرة</h2><p class="sub">تعرض أحدث 100 كود مع حالتها فقط. حذف الأكواد المستخدمة ممنوع حفاظاً على سجل التفعيل.</p>
      <?php if (!$codes): ?><div class="empty">لم تُصدر أي أكواد بعد.</div><?php endif;?>
      <?php foreach ($codes as $c): ?>
      <article class="item">
        <div class="item-head"><div><h3>كود #<?=(int)$c['id']?> — <?=((int)$c['months']===3?'3 أشهر':'شهر')?> </h3><small>مجموعة: <?=e($c['batch_label']??'—')?> • تاريخ الإصدار <?=e($c['created_at'])?></small></div><span class="badge <?=($c['status']==='available'?'live':'')?>"><?=e($c['status'])?></span></div>
        <p class="sub">صالح للإدخال حتى: <?=e($c['valid_until']??'بدون تاريخ')?> <?php if ($c['redeemed_by']): ?>• استُخدم بواسطة الطالب #<?=(int)$c['redeemed_by']?><?php endif;?></p>
        <?php if ($c['status']==='available'): ?>
        <div class="actions"><form method="post" onsubmit="return confirm('إلغاء الكود ومنع استخدامه؟')"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="revoke_code"><input type="hidden" name="id" value="<?=(int)$c['id']?>"><button type="submit" class="warn">إلغاء التفعيل</button></form></div>
        <details><summary>تعديل المجموعة والصلاحية</summary>
          <form method="post"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="edit_code"><input type="hidden" name="id" value="<?=(int)$c['id']?>">
            <div class="grid"><div class="field"><label>المجموعة</label><input name="batch" value="<?=e($c['batch_label']??'admin')?>" required></div><div class="field"><label>آخر يوم للإدخال</label><input type="date" name="valid_until" value="<?=e($c['valid_until']?substr($c['valid_until'],0,10):'')?>"></div></div>
            <button type="submit" class="secondary">حفظ التعديل</button>
          </form></details>
        <?php elseif ($c['status']==='revoked'): ?>
          <form method="post" class="actions" onsubmit="return confirm('حذف سجل الكود الملغى نهائياً؟')"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="delete_code"><input type="hidden" name="id" value="<?=(int)$c['id']?>"><input type="hidden" name="confirm" value="DELETE"><button type="submit" class="danger">حذف السجل</button></form>
        <?php endif;?>
      </article>
      <?php endforeach;?>
    </section>
  <?php endif;?>

  <?php if ($section === 'ads' && $canAds): ?>
    <section class="panel">
      <h2>إضافة إعلان مدرسة</h2>
      <p class="sub">السعر ثابت: 5,000 د.ع لكل يوم. تظهر الإعلانات المتاحة تلقائياً للطلاب عبر نظام الموقع.</p>
      <form method="post">
        <input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="create_ad">
        <div class="field"><label>اسم المدرسة</label><input name="school_name" maxlength="180" required></div>
        <div class="field"><label>نص الإعلان</label><textarea name="ad_text" maxlength="500" required></textarea></div>
        <div class="grid"><div class="field"><label>بداية الإعلان</label><input type="date" name="start_date" required></div><div class="field"><label>نهاية الإعلان</label><input type="date" name="end_date" required></div></div>
        <div class="field"><label>مسار صورة موجودة على الموقع (اختياري)</label><input name="image_path" maxlength="255" placeholder="/uploads/school-ads/example.jpg"></div>
        <button type="submit">+ نشر الإعلان</button>
      </form>
    </section>
    <section class="panel"><h2>إدارة الإعلانات</h2><p class="sub">يمكن تعديل الإعلان، إيقافه مؤقتاً، تفعيله مجدداً، أو حذفه.</p>
      <?php if (!$ads): ?><div class="empty">لا توجد إعلانات مدارس.</div><?php endif;?>
      <?php foreach ($ads as $ad): ?>
        <article class="item">
          <div class="item-head"><div><h3><?=e($ad['school_name'])?></h3><small>رقم <?=(int)$ad['id']?> • <?=e($ad['start_date'])?> ← <?=e($ad['end_date'])?> • <?=number_format((int)$ad['total_price'])?> د.ع</small></div><span class="badge <?=($ad['is_enabled']?'live':'')?>"><?=($ad['is_enabled']?'مفعّل':'متوقف')?></span></div>
          <p><?=e($ad['ad_text'])?></p><small>عدد المشاهدات: <?=(int)$ad['impressions']?></small>
          <div class="actions">
            <form method="post"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="toggle_ad"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><button type="submit" class="secondary"><?=($ad['is_enabled']?'إيقاف الإعلان':'تفعيل الإعلان')?></button></form>
            <form method="post" onsubmit="return confirm('هل تريد حذف الإعلان نهائياً؟')"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="delete_ad"><input type="hidden" name="id" value="<?=(int)$ad['id']?>"><input type="hidden" name="confirm" value="DELETE"><button type="submit" class="danger">حذف</button></form>
          </div>
          <details><summary>تعديل بيانات الإعلان</summary>
            <form method="post"><input type="hidden" name="csrf" value="<?=e($csrfValue)?>"><input type="hidden" name="action" value="edit_ad"><input type="hidden" name="id" value="<?=(int)$ad['id']?>">
              <div class="field"><label>المدرسة</label><input name="school_name" maxlength="180" value="<?=e($ad['school_name'])?>" required></div>
              <div class="field"><label>النص</label><textarea name="ad_text" maxlength="500" required><?=e($ad['ad_text'])?></textarea></div>
              <div class="grid"><div class="field"><label>من</label><input name="start_date" type="date" value="<?=e($ad['start_date'])?>" required></div><div class="field"><label>إلى</label><input name="end_date" type="date" value="<?=e($ad['end_date'])?>" required></div></div>
              <div class="field"><label>الصورة</label><input name="image_path" maxlength="255" value="<?=e($ad['image_path']??'')?>"></div>
              <button type="submit" class="secondary">حفظ التعديلات</button>
            </form>
          </details>
        </article>
      <?php endforeach;?>
    </section>
  <?php endif;?>
  <p class="sub">جميع عمليات الإدارة تتم على خادم المرشد الوزاري وبحساب المدير المصرح له. يُفضّل تسجيل الخروج من لوحة الموقع بعد الانتهاء.</p>
</main></body></html>
