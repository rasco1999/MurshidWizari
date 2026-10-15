<?php
declare(strict_types=1);

if(session_status()===PHP_SESSION_NONE){
    // تقوية PHP Session قبل session_start: لا Session ID في URL، Cookies فقط،
    // رفض المعرفات غير المنشأة من الخادم، HttpOnly، وSecure على HTTPS.
    @ini_set('session.use_strict_mode','1');
    @ini_set('session.use_only_cookies','1');
    @ini_set('session.use_trans_sid','0');
    @ini_set('session.cookie_httponly','1');
    @ini_set('session.cookie_samesite','Lax');
    @ini_set('session.cookie_lifetime','0');
    @ini_set('session.sid_length','48');
    @ini_set('session.sid_bits_per_character','6');
    $secure=(!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off')||((string)($_SERVER['HTTP_X_FORWARDED_PROTO']??'')==='https');
    if($secure)@ini_set('session.cookie_secure','1');
}
require_once __DIR__ . '/topic_catalog.php';
require_once __DIR__ . '/platform_layout.php';
require_once __DIR__ . '/answer_grading_v2.php';
require_once __DIR__ . '/hint_free.php';

/** Strip all coaching / step hints from problem payloads, preserving correct answers and post-answer explanations. */
function murshid_without_hints($value) {
    return murshid_strip_hints($value);
}

function MAD_TYPES(): array {
    // تظهر بهذه الصيغة للطالب والمدير حتى يكون الاختيار مطابقاً لصياغة المنهج.
    return [
        'مد طبيعي',
        'مد بدل',
        'مد متصل',
        'مد منفصل',
        'مد لازم',
        'مد عارض للسكون',
        'مد لين',
    ];
}

function normalize_mad_type(string $value): string {
    $value = trim($value);
    $map = [
        'الطبيعي'=>'مد طبيعي', 'مد الطبيعي'=>'مد طبيعي', 'مد طبيعي'=>'مد طبيعي',
        'البدل'=>'مد بدل', 'مد البدل'=>'مد بدل', 'مد بدل'=>'مد بدل',
        'المتصل'=>'مد متصل', 'مد المتصل'=>'مد متصل', 'مد متصل'=>'مد متصل',
        'المنفصل'=>'مد منفصل', 'مد المنفصل'=>'مد منفصل', 'مد منفصل'=>'مد منفصل',
        'اللازم'=>'مد لازم', 'مد اللازم'=>'مد لازم', 'مد لازم'=>'مد لازم',
        'العارض للسكون'=>'مد عارض للسكون', 'مد العارض للسكون'=>'مد عارض للسكون', 'مد عارض للسكون'=>'مد عارض للسكون',
        'اللين'=>'مد لين', 'مد اللين'=>'مد لين', 'مد لين'=>'مد لين',
    ];
    return $map[$value] ?? $value;
}

function decode_match_answer(string $raw): array {
    if ($raw === '') return [];
    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) return [];
    $items = $decoded['items'] ?? $decoded;
    return is_array($items) ? $items : [];
}

function is_match_answer_correct(PDO $pdo, int $questionId, string $raw): bool {
    $items = decode_match_answer($raw);
    if (!$items) return false;
    $q = $pdo->prepare("SELECT id,correct_type FROM match_items WHERE question_id=? ORDER BY sort_order,id");
    $q->execute([$questionId]);
    $expected = $q->fetchAll(PDO::FETCH_ASSOC);
    if (!$expected) return false;
    foreach ($expected as $row) {
        $id = (string)(int)$row['id'];
        $given = $items[$id] ?? $items[(int)$row['id']] ?? null;
        if (!is_array($given)) return false;
        $type = normalize_mad_type((string)($given['type'] ?? ''));
        $reason = trim((string)($given['reason'] ?? ''));
        // نوع المد يصحح آلياً، والسبب يجب أن يكتبه الطالب ثم يرى النموذج بعد التسليم.
        if ($type === '' || $reason === '' || $type !== normalize_mad_type((string)$row['correct_type'])) return false;
    }
    return true;
}

function match_model_answer(PDO $pdo, int $questionId): string {
    $q = $pdo->prepare("SELECT mad_word,correct_type,reason_text FROM match_items WHERE question_id=? ORDER BY sort_order,id");
    $q->execute([$questionId]);
    $parts=[];
    foreach($q->fetchAll(PDO::FETCH_ASSOC) as $row){
        $part=(string)$row['mad_word'].' — '.normalize_mad_type((string)$row['correct_type']);
        if(trim((string)($row['reason_text']??''))!=='') $part.=' — '.trim((string)$row['reason_text']);
        $parts[]=$part;
    }
    return implode(' | ',$parts);
}

function ago(?string $datetime): string {
    if (!$datetime) return '';
    $diff = time() - strtotime($datetime);
    if ($diff < 60) return 'الآن';
    if ($diff < 3600) return 'قبل ' . (int)round($diff / 60) . ' دقيقة';
    if ($diff < 86400) return 'قبل ' . (int)round($diff / 3600) . ' ساعة';
    return 'قبل ' . (int)round($diff / 86400) . ' يوم';
}

function subject_icon(string $name): string {
    $map = [
        'إسلامية' => '🕌', 'عربية' => '📖', 'رياضيات' => '📐',
        'إنكليزية' => '🌐', 'أحياء' => '🧬',
        'كيمياء' => '⚗️', 'فيزياء' => '⚛️', 'اجتماعيات' => '🗺️',
        'تاريخ' => '🏛️', 'جغرافية' => '🗺️', 'اقتصاد' => '💹',
    ];
    foreach ($map as $needle => $icon) {
        if (mb_strpos($name, $needle) !== false) return $icon;
    }
    return '📘';
}

function e($value): string {
    // تقبل القيم النصية والرقمية والمنطقية و null بأمان في جميع القوالب.
    // هذا يمنع TypeError مع strict_types عند تمرير أرقام مثل عدد الصفحات أو النقاط.
    if ($value === null) return '';
    if (is_bool($value)) $value = $value ? '1' : '0';
    elseif (is_int($value) || is_float($value) || is_string($value)) $value = (string)$value;
    elseif ($value instanceof Stringable) $value = (string)$value;
    else $value = '';
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

function redirect(string $path): never {
    header('Location: ' . BASE_URL . $path);
    exit;
}

function json_response(array $data, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function csrf_token(): string {
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return (string)$_SESSION['csrf'];
}

function verify_csrf(?string $token): bool {
    return is_string($token) && $token !== '' && isset($_SESSION['csrf']) && hash_equals((string)$_SESSION['csrf'], $token);
}

function require_post_csrf(): void {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !verify_csrf($_POST['csrf'] ?? null)) {
        http_response_code(419);
        exit('انتهت صلاحية الطلب. حدّث الصفحة وحاول مرة أخرى.');
    }
}

function rate_limit(string $key, int $max, int $windowSeconds): bool {
    $now = time();
    // حد مشترك على مستوى الخادم حتى لا يستطيع المهاجم تجاوزه بحذف Cookie.
    try{
        $pdo=db();$pdo->exec("CREATE TABLE IF NOT EXISTS request_rate_limits (scope_key CHAR(64) PRIMARY KEY,hit_count INT UNSIGNED NOT NULL DEFAULT 0,window_started_at INT UNSIGNED NOT NULL,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_rate_updated(updated_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $ip=function_exists('murshid_client_ip')?murshid_client_ip():substr((string)($_SERVER['REMOTE_ADDR']??'unknown'),0,45);$uid=(int)($_SESSION['user_id']??0);$scope=hash('sha256',$key."\0".$ip."\0".$uid);$own=!$pdo->inTransaction();if($own)$pdo->beginTransaction();$pdo->prepare('INSERT IGNORE INTO request_rate_limits(scope_key,hit_count,window_started_at) VALUES(?,0,?)')->execute([$scope,$now]);$q=$pdo->prepare('SELECT hit_count,window_started_at FROM request_rate_limits WHERE scope_key=? FOR UPDATE');$q->execute([$scope]);$row=$q->fetch()?:['hit_count'=>0,'window_started_at'=>$now];$count=(int)$row['hit_count'];$start=(int)$row['window_started_at'];if($now-$start>=$windowSeconds){$count=0;$start=$now;}$count++;$pdo->prepare('UPDATE request_rate_limits SET hit_count=?,window_started_at=? WHERE scope_key=?')->execute([$count,$start,$scope]);if($own)$pdo->commit();return $count<=$max;
    }catch(Throwable $e){if(isset($pdo)&&$pdo instanceof PDO&&$pdo->inTransaction())try{$pdo->rollBack();}catch(Throwable $x){} }
    // احتياط داخل الجلسة إذا كانت قاعدة البيانات غير متاحة.
    $bucket = $_SESSION['_rate'][$key] ?? ['count'=>0,'start'=>$now];
    if (!is_array($bucket) || $now - (int)($bucket['start'] ?? 0) >= $windowSeconds) {
        $bucket = ['count'=>0,'start'=>$now];
    }
    $bucket['count'] = (int)$bucket['count'] + 1;
    $_SESSION['_rate'][$key] = $bucket;
    return $bucket['count'] <= $max;
}

function normalize_login_identifier(string $identifier): string {
    $identifier = trim($identifier);
    $identifier = strtr($identifier, [
        '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
        '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
    ]);

    if (str_contains($identifier, '@')) {
        return function_exists('mb_strtolower')
            ? mb_strtolower($identifier, 'UTF-8')
            : strtolower($identifier);
    }

    $phone = preg_replace('/[\s\-\(\)]+/', '', $identifier) ?? $identifier;
    if (preg_match('/^(?:\+964|00964)(7\d{9})$/', $phone, $m)) return '0' . $m[1];
    if (preg_match('/^7\d{9}$/', $phone)) return '0' . $phone;
    return $phone;
}

function is_valid_iraqi_phone(string $phone): bool {
    return (bool)preg_match('/^07[0-9]{9}$/', normalize_login_identifier($phone));
}

function is_valid_email(string $email): bool {
    return filter_var(normalize_login_identifier($email), FILTER_VALIDATE_EMAIL) !== false;
}

/*
|--------------------------------------------------------------------------
| التحقق من وجود البريد أو رقم الهاتف
|--------------------------------------------------------------------------
| تم إصلاح مشكلة SQLSTATE[HY093]
| باستخدام معاملين مختلفين :email و :phone
*/

function login_identifier_exists(string $identifier): bool {

    $identifier = normalize_login_identifier($identifier);

    $stmt = db()->prepare(
        "SELECT id
         FROM users
         WHERE email = :email
            OR phone = :phone
         LIMIT 1"
    );

    $stmt->execute([
        'email' => $identifier,
        'phone' => $identifier
    ]);

    return (bool)$stmt->fetchColumn();
}

function current_user(): ?array {

    if (empty($_SESSION['user_id'])) {
        if (!function_exists('remember_login_restore') || !remember_login_restore()) return null;
    }

    // حماية جلسة واحدة لا تعتمد على استدعاء enforce_session_security من config.php فقط.
    // أي صفحة/API تستدعي current_user ستتحقق من قفل الحساب مباشرة قبل جلب بياناته.
    if (function_exists('single_session_validate_current')) {
        $guard = single_session_validate_current((int)$_SESSION['user_id']);
        if (empty($guard['ok'])) {
            $notice = !empty($guard['account_in_use']) || !empty($guard['device_mismatch'])
                ? 'تم إنهاء هذه الجلسة لأن الحساب مرتبط بجلسة نشطة في متصفح أو جهاز آخر.'
                : (!empty($guard['guard_error'])
                    ? 'تعذر التحقق من حماية الحساب. سجّل الدخول مرة أخرى.'
                    : 'انتهت صلاحية هذه الجلسة. سجّل الدخول مرة أخرى.');
            if (function_exists('single_session_invalidate_local')) single_session_invalidate_local($notice);
            else { $_SESSION = []; $_SESSION['_session_notice'] = $notice; }
            // إذا اختار الطالب «تذكرني على هذا الجهاز»، أعد جلسة جديدة آمنة
            // بعد انتهاء جلسة الخمول بدلاً من مطالبته بكلمة المرور من جديد.
            if (!function_exists('remember_login_restore') || !remember_login_restore()) return null;
        }
    }

    // حماية ضد أي انقطاع عابر في الاتصال بقاعدة البيانات: بدل أن تُسقط هذه
    // الدالة الصفحة كاملة (وهي تُستدعى في كل صفحة محمية عبر require_student/
    // require_admin/require_teacher)، نتعامل مع أي استثناء هنا كأن المستخدم
    // غير مسجل دخول حالياً، فتُعاد محاولة الصفحة بأمان بدل شاشة الخطأ العامة.
    try {
        $stmt = db()->prepare(
            "SELECT
                u.*,
                g.name AS grade_name
             FROM users u
             LEFT JOIN grades g ON g.id = u.grade_id
             WHERE u.id = ?
             LIMIT 1"
        );

        $stmt->execute([
            (int)$_SESSION['user_id']
        ]);
    } catch (Throwable $e) {
        error_log('current_user query failed: ' . $e->getMessage());
        return null;
    }

    return $stmt->fetch() ?: null;
}

function is_subscribed(int $userId): bool {
    try {
        $stmt = db()->prepare(
            "SELECT id
             FROM subscriptions
             WHERE user_id = ?
               AND status = 'active'
               AND (expires_at IS NULL OR expires_at > NOW())
             LIMIT 1"
        );
        $stmt->execute([$userId]);
        return (bool)$stmt->fetchColumn();
    } catch (Throwable $e) {
        error_log('is_subscribed query failed: ' . $e->getMessage());
        return false;
    }
}

function require_student(): array {

    $u = current_user();

    if (
        !$u ||
        $u['role'] !== 'student' ||
        $u['status'] !== 'active'
    ) {
        redirect('/');
    }

    return $u;
}

function require_teacher(): array {
    $u = current_user();
    if (!$u || !in_array($u['role'], ['teacher','admin'], true) || $u['status'] !== 'active') { redirect('/index.php'); }
    return $u;
}

function require_admin(): array {

    $u = current_user();

    if (
        !$u ||
        $u['role'] !== 'admin' ||
        $u['status'] !== 'active'
    ) {
        redirect('/admin/index.php');
    }
    if(!empty($_SESSION['admin_2fa_pending'])&&basename((string)($_SERVER['SCRIPT_NAME']??''))!=='verify-2fa.php')redirect('/admin/verify-2fa.php');

    // صلاحيات دقيقة للوحة الإدارة مع رفض افتراضي لأي صفحة إدارية غير معروفة.
    $tools=__DIR__.'/platform_tools.php';
    if(is_file($tools)){
        require_once $tools;
        $page=basename((string)($_SERVER['SCRIPT_NAME']??''));
        // التحقق الثنائي اختياري؛ إذا كان مفعّلًا يطبّق بعد تسجيل الدخول من بوابة الإدارة.
        $caps=[
          'dashboard.php'=>'dashboard','analytics.php'=>'analytics','change_password.php'=>'dashboard',
          'questions.php'=>'questions','question-workflow.php'=>'questions','question-revisions.php'=>'questions','question-requests.php'=>'question_requests','question-integrity.php'=>'questions',
          'question-reports.php'=>'reports','avatar-moderation.php'=>'avatars','avatar-file.php'=>'avatars',
          'curriculum.php'=>'curriculum','subjects.php'=>'curriculum','seasons.php'=>'seasons','contest.php'=>'contest','school-ads.php'=>'school_ads','stories.php'=>'dashboard',
          'support.php'=>'support','users.php'=>'users','teachers.php'=>'users','account-requests.php'=>'account_requests',
          'subscriptions.php'=>'subscriptions','profits.php'=>'profits','codes.php'=>'codes',
          'question-bank-update-v28.php'=>'imports','question-bank-update-v29.php'=>'imports',
          'backup.php'=>'owner','team-access.php'=>'owner','upgrade-v43.php'=>'owner','audit.php'=>'owner','security.php'=>'owner','system-health.php'=>'owner','content-audit.php'=>'questions','predictions.php'=>'questions','remove-french-v48.php'=>'owner','payment-gateways.php'=>'profits','wayl-status.php'=>'payments','db-diagnostic.php'=>'owner'
        ];
        $unguarded=['index.php','verify-2fa.php'];
        $script=(string)($_SERVER['SCRIPT_NAME']??'');
        // خريطة الصفحات تطبق داخل /admin فقط؛ نقاط API الحساسة تستخدم require_admin_capability صراحة.
        if(str_contains($script,'/admin/') && !in_array($page,$unguarded,true)){
            $cap=$caps[$page]??null;
            if($cap===null || !admin_can(db(),(int)$u['id'],$cap)){http_response_code(403);exit('لا يملك حسابك صلاحية فتح هذه الصفحة.');}
        }
    }

    return $u;
}

function enforce_session_security(): void {
    if (session_status() !== PHP_SESSION_ACTIVE) return;
    $now = time();
    $idle = defined('SESSION_IDLE_TIMEOUT') ? max(300,(int)SESSION_IDLE_TIMEOUT) : 7200; // student inactivity ignored; absolute expiry remains
    $absolute = defined('SESSION_ABSOLUTE_TIMEOUT') ? max($idle,(int)SESSION_ABSOLUTE_TIMEOUT) : 43200;

    if (!empty($_SESSION['user_id'])) {
        $userId = (int)$_SESSION['user_id'];
        $started = (int)($_SESSION['_auth_started_at'] ?? $now);
        $last = (int)($_SESSION['_auth_last_at'] ?? $now);

        // Admin idle expiration or absolute session expiry: release the account guard.
        if ((SESSION_SCOPE !== 'student' && ($now - $last) > $idle) || ($now - $started) > $absolute) {
            if (function_exists('single_session_release_current')) {
                try { single_session_release_current(); } catch (Throwable $e) { error_log('session timeout release: '.$e->getMessage()); }
            }
            $_SESSION = [];
            session_regenerate_id(true);
            $_SESSION['_session_expired'] = 1;
            $_SESSION['_session_notice'] = 'انتهت صلاحية الجلسة. سجّل الدخول مجدداً.';
            return;
        }

        // التحقق المركزي: هذه الجلسة يجب أن تكون الوحيدة المسجلة للحساب في قاعدة البيانات.
        if (function_exists('single_session_validate_current')) {
            $guard = single_session_validate_current($userId);
            if (empty($guard['ok'])) {
                $notice = !empty($guard['account_in_use'])
                    ? 'تم إنهاء هذه الجلسة لأن الحساب لديه جلسة فعالة أخرى.'
                    : (!empty($guard['guard_error'])
                        ? 'تعذر التحقق من حماية الجلسة. سجّل الدخول مرة أخرى.'
                        : 'انتهت صلاحية هذه الجلسة. سجّل الدخول مرة أخرى.');
                if (function_exists('single_session_invalidate_local')) single_session_invalidate_local($notice);
                else {
                    $_SESSION = [];
                    session_regenerate_id(true);
                    $_SESSION['_session_notice'] = $notice;
                }
                return;
            }
        }

        $_SESSION['_auth_started_at'] = $started;
        $_SESSION['_auth_last_at'] = $now;
    }
}
function profile(int $userId): array {
    $fallback=['user_id'=>$userId,'avatar_path'=>null,'avatar_code'=>'boy1','name_changed_at'=>null,'avatar_changed_at'=>null];
    try {
        $q=db()->prepare("SELECT * FROM user_profiles WHERE user_id=? LIMIT 1");
        $q->execute([$userId]);
        $p=$q->fetch();
        if(!$p){
            db()->prepare("INSERT IGNORE INTO user_profiles(user_id) VALUES(?)")->execute([$userId]);
            return $fallback;
        }
        return array_merge($fallback,$p);
    } catch (Throwable $e) {
        // لا نسمح لمشكلة ترقية مؤقتة في جدول الصورة بإسقاط لوحة الطالب أو الإدارة.
        error_log('profile fallback: '.$e->getMessage());
        return $fallback;
    }
}
function avatar_url(?string $path): string {
    if (!$path) return '';
    $file = basename($path);
    if (!preg_match('/^[a-zA-Z0-9._-]+\.(?:jpe?g|png|webp)$/i', $file)) return '';
    $absolute = __DIR__ . '/../storage/avatars/' . $file;
    if (!is_file($absolute)) return '';
    $version = (string) ((int) (@filemtime($absolute) ?: time()));
    // storage محمي بالكامل من الويب؛ تمر الصور المعتمدة فقط عبر وسيط يتحقق من قاعدة البيانات.
    return BASE_URL . '/avatar.php?f=' . rawurlencode($file) . '&v=' . rawurlencode($version);
}
function student_avatar_catalog(?string $gender=null): array {
    $boys = [
        'boy1'=>'الصقر','boy2'=>'المفكر','boy3'=>'الهيبة','boy4'=>'القائد','boy5'=>'المهندس','boy6'=>'الأسطورة',
    ];
    $girls = [
        'girl1'=>'لؤلؤة','girl2'=>'زمردة','girl3'=>'شمس','girl4'=>'الملكة','girl5'=>'الباحثة','girl6'=>'وهج',
    ];
    if ($gender==='male') return $boys;
    if ($gender==='female') return $girls;
    return $boys + $girls;
}
function student_avatar_code(?string $code, ?string $gender=null): string {
    $catalog=student_avatar_catalog();
    if ($code && isset($catalog[$code])) return $code;
    return $gender==='female' ? 'girl1' : 'boy1';
}
function student_avatar_url(?string $code, ?string $gender=null): string {
    return BASE_URL . '/assets/img/default-user.svg?v=20260928-v56';
}
function ui_icon(string $name, string $class = ''): string {
    static $paths = [
        'megaphone'=>'<path d="M3 11v2a2 2 0 0 0 2 2h2l2 5h3l-2-5 9-3V6L7 9H5a2 2 0 0 0-2 2Z"/><path d="M19 8.5c1 .7 1.6 1.8 1.6 3s-.6 2.3-1.6 3"/>',
        'home'=>'<path d="M3.5 10.8 12 3.5l8.5 7.3"/><path d="M5.5 9.9V20h13V9.9"/><path d="M9.2 20v-6.2h5.6V20"/>',
        'target'=>'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.7"/><circle cx="12" cy="12" r="1.4"/><path d="M18.5 5.5 21 3m-2.8.8 2.8-.8-.8 2.8"/>',
        'brain'=>'<path d="M9.5 4.4A3.3 3.3 0 0 0 4.7 7a3.4 3.4 0 0 0 .5 6.2 3.4 3.4 0 0 0 4.3 4.4V4.4Z"/><path d="M14.5 4.4A3.3 3.3 0 0 1 19.3 7a3.4 3.4 0 0 1-.5 6.2 3.4 3.4 0 0 1-4.3 4.4V4.4Z"/><path d="M9.5 8.5H7.7m6.8 3.1h2.2m-7.2 2.8H8"/>',
        'trophy'=>'<path d="M8 4h8v5.2a4 4 0 0 1-8 0V4Z"/><path d="M8 6H4v1.2a4 4 0 0 0 4 4.1m8-5.3h4v1.2a4 4 0 0 1-4 4.1M12 13.2v4.2M8.5 20h7"/>',
        'chart'=>'<path d="M4 20V11m5 9V7m5 13V4m5 16v-6"/><path d="M2.5 20.5h19"/>',
        'book'=>'<path d="M3.5 5.5A3.5 3.5 0 0 1 7 3h4v17H7a3.5 3.5 0 0 0-3.5 2.5v-17Zm17 0A3.5 3.5 0 0 0 17 3h-4v17h4a3.5 3.5 0 0 1 3.5 2.5v-17Z"/>',
        'user'=>'<circle cx="12" cy="8" r="3.6"/><path d="M4.3 20.5a7.7 7.7 0 0 1 15.4 0"/>',
        'settings'=>'<circle cx="12" cy="12" r="3"/><path d="M19.2 13.4v-2.8l-2-1 .1-2.2-2.4-1.4-1.8 1.2L11.2 6 8.8 7.4 9 9.6l-2 1v2.8l2 1-.2 2.2 2.4 1.4 1.8-1.2 1.9 1.2 2.4-1.4-.1-2.2 2-1Z"/>',
        'search'=>'<circle cx="10.5" cy="10.5" r="6.4"/><path d="m15.4 15.4 5.1 5.1"/>',
        'bell'=>'<path d="M18.2 9.5a6.2 6.2 0 0 0-12.4 0c0 6.4-2.7 6.8-2.7 8.7h17.8c0-1.9-2.7-2.3-2.7-8.7Z"/><path d="M9.7 21h4.6"/>',
        'menu'=>'<path d="M4 7h16M4 12h16M4 17h16"/>',
        'close'=>'<path d="m6 6 12 12M18 6 6 18"/>',
        'users'=>'<circle cx="9" cy="8" r="3"/><path d="M3.2 20a5.8 5.8 0 0 1 11.6 0m2.1-13.7a2.8 2.8 0 0 1 0 5.4m-.3 3.2a4.8 4.8 0 0 1 4.1 4.8"/>',
        'card'=>'<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M3 9.5h18M7 15h4"/>',
        'checklist'=>'<rect x="4" y="3.5" width="16" height="17" rx="2.5"/><path d="m7.2 8.1 1.4 1.4 2.4-2.8M13 8h4m-9.8 6.1 1.4 1.4 2.4-2.8M13 14h4"/>',
        'teacher'=>'<path d="m12 3 9 4.4-9 4.4-9-4.4L12 3Z"/><path d="M6.5 10.1v4.2c0 2 2.5 3.7 5.5 3.7s5.5-1.7 5.5-3.7v-4.2M21 7.5v5"/>',
        'chat'=>'<path d="M4 5.2h16v11.5H9l-5 3.8V5.2Z"/><path d="M8 10h8M8 13.5h5"/>',
        'plus'=>'<path d="M12 5v14M5 12h14"/>',
        'shield'=>'<path d="M12 3 5 6.2v5.1c0 4.8 2.8 7.8 7 9.7 4.2-1.9 7-4.9 7-9.7V6.2L12 3Z"/><path d="m8.8 12.1 2 2.1 4.5-5"/>',
        'logout'=>'<path d="M10 4H5v16h5m4-4 4-4-4-4m4 4H9"/>',
        'arrow'=>'<path d="m9 18 6-6-6-6"/>',
        'calendar'=>'<rect x="3" y="5" width="18" height="16" rx="2.3"/><path d="M7 3v4m10-4v4M3 10h18"/>',
        'check'=>'<path d="m5 12 4 4L19 6"/>',
        'phone'=>'<rect x="7" y="2.5" width="10" height="19" rx="2.3"/><path d="M10.5 18.3h3"/>',
        'mail'=>'<rect x="3" y="5" width="18" height="14" rx="2.3"/><path d="m4.2 7.2 7.8 5.7 7.8-5.7"/>',
        'lock'=>'<rect x="5" y="10" width="14" height="11" rx="2.3"/><path d="M8 10V7.2a4 4 0 0 1 8 0V10M12 14v3"/>',
        'eye'=>'<path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z"/><circle cx="12" cy="12" r="2.8"/>',
        'layers'=>'<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>',
        'mosque'=>'<path d="M4 21h16M6 21V10h12v11M9 21v-5h6v5M8 10c0-2.5 1.8-4.5 4-5.5 2.2 1 4 3 4 5.5M12 4.5V2.5"/><path d="M10.3 2.5h3.4"/>',
        'language'=>'<path d="M5 4h10M10 4c0 6-2.5 10-6 13m3-7c1.6 2.5 4 4.8 7 6"/><path d="m15 20 3.5-8 3.5 8m-5.7-3h4.4"/>',
        'globe'=>'<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
        'calculator'=>'<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 7h8M8 11h2m4 0h2M8 15h2m4 0h2M8 19h2m4 0h2"/>',
        'dna'=>'<path d="M7 3c6 4 4 14 10 18M17 3C11 7 13 17 7 21M8.4 6h7.2M8 18h8M9 10h6M9 14h6"/>',
        'flask'=>'<path d="M9 3h6M10 3v6l-5.5 9.2A1.8 1.8 0 0 0 6 21h12a1.8 1.8 0 0 0 1.5-2.8L14 9V3"/><path d="M7.5 15h9"/>',
        'atom'=>'<circle cx="12" cy="12" r="1.5"/><ellipse cx="12" cy="12" rx="9" ry="4.2"/><ellipse cx="12" cy="12" rx="9" ry="4.2" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="9" ry="4.2" transform="rotate(120 12 12)"/>',
        'map'=>'<path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z"/><path d="M9 3v15m6-12v15"/>',
        'landmark'=>'<path d="m3 9 9-5 9 5M4 10h16M6 10v7m4-7v7m4-7v7m4-7v7M3 20h18"/>',
        'economy'=>'<path d="M4 19V9m6 10V5m6 14v-7m4 7H2"/><path d="m4 12 5-4 4 3 7-7"/>',
        'timer'=>'<circle cx="12" cy="13" r="8"/><path d="M9 2h6M12 5V2m0 11 3-3"/>',
        'sword'=>'<path d="m14 4 6-1-1 6-9.5 9.5-4-4L15 5"/><path d="m6 15-3 3m4 1-2 2"/>',
        'refresh'=>'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M18.4 9A7 7 0 0 0 6.2 6.2L4 9m2 6a7 7 0 0 0 11.8 2.8L20 15"/>',
        'celebrate'=>'<path d="m4 20 5-12 7 7L4 20Z"/><path d="M14 4h.01M19 7h.01M17 2v3M21 12h-3M10 3l1.5 2.5"/>',
        'medal'=>'<circle cx="12" cy="14" r="5"/><path d="m9 9-3-6h4l2 4 2-4h4l-3 6M10 14l1.3 1.3L14 12.5"/>',
        'fire'=>'<path d="M12 22c4 0 7-2.8 7-7 0-3-1.6-5.6-4.5-8.5.1 2.7-1 4.2-2.2 5.1.1-3.8-2-6.4-4.1-8.6.1 3.7-3.2 6.1-3.2 10.7C5 18.4 8 22 12 22Z"/><path d="M12 19c1.8 0 3-1.3 3-3.2 0-1.2-.6-2.4-1.8-3.6 0 1.4-.6 2.2-1.2 2.7 0-1.8-.8-3.1-1.8-4.2 0 2-1.2 3.2-1.2 5.1 0 1.9 1.2 3.2 3 3.2Z"/>',
        'ticket'=>'<path d="M4 6h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4V6Z"/><path d="M12 6v12"/>',
        'activity'=>'<path d="M3 12h4l2-6 4 12 2-6h6"/>',
        'edit'=>'<path d="M4 20h4l11-11-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/>',
        'database'=>'<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/>',
        'analytics'=>'<path d="M4 20V10m5 10V5m5 15v-8m5 8V8"/><path d="m3 7 5-3 5 4 7-5"/>',
        'security'=>'<path d="M12 3 5 6v5c0 5 3 8 7 10 4-2 7-5 7-10V6l-7-3Z"/><path d="M9 11V9a3 3 0 0 1 6 0v2M8 11h8v6H8z"/>',
        'question'=>'<circle cx="12" cy="12" r="9"/><path d="M9.8 9a2.4 2.4 0 1 1 3.2 2.2c-.8.35-1 1-1 1.8M12 17h.01"/>',
        'clipboard'=>'<rect x="5" y="5" width="14" height="16" rx="2"/><path d="M9 5V3h6v2M8 10h8M8 14h8M8 18h5"/>',
        'star'=>'<path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9L12 3Z"/>',
        'app-home'=>'<path d="M4.3 10.7 12 4l7.7 6.7v8.2a1.8 1.8 0 0 1-1.8 1.8H6.1a1.8 1.8 0 0 1-1.8-1.8v-8.2Z"/><path d="M9.2 20.7v-6h5.6v6"/><path d="m16.8 5.9 1.6-1.6"/>',
        'app-library'=>'<path d="M4 5.2c2.5-.8 5-.4 8 1.3v13c-3-1.7-5.5-2.1-8-1.3v-13Z"/><path d="M20 5.2c-2.5-.8-5-.4-8 1.3v13c3-1.7 5.5-2.1 8-1.3v-13Z"/><path d="M7 9h2.5M14.5 9H17"/>',
        'app-stats'=>'<path d="M4 19.5V13h3v6.5H4Zm6.5 0V8h3v11.5h-3Zm6.5 0V4.5h3v15h-3Z"/><path d="M3 4.5h4l2.2 2.4 3.4-3.1 3.1 2.4 4.3-3.7"/>',
        'app-profile'=>'<circle cx="12" cy="9" r="3.4"/><path d="M5.2 20.3c.8-4 3.2-6.1 6.8-6.1s6 2.1 6.8 6.1"/><circle cx="12" cy="12" r="10"/>',
        'app-more'=>'<circle cx="6" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="18" cy="12" r="1.4"/>',
    ];
    $body=$paths[$name]??$paths['book'];
    $safeClass=preg_replace('/[^a-zA-Z0-9 _-]/','',$class);
    return '<svg class="icon'.($safeClass!==''?' '.htmlspecialchars($safeClass,ENT_QUOTES,'UTF-8'):'').'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'.$body.'</svg>';
}
function subject_ui_icon(string $name, string $class = ''): string {
    $map = [
        'إسلامية'=>'mosque','عربية'=>'language','رياضيات'=>'calculator',
        'إنكليزية'=>'globe','أحياء'=>'dna','كيمياء'=>'flask',
        'فيزياء'=>'atom','اجتماعيات'=>'map','تاريخ'=>'landmark','جغرافية'=>'map',
        'اقتصاد'=>'economy','نقد'=>'book','أدب'=>'book'
    ];
    foreach($map as $needle=>$icon) if(mb_strpos($name,$needle)!==false) return ui_icon($icon,$class);
    return ui_icon('book',$class);
}

function achievement_ui_icon(string $code, string $class = ''): string {
    $map = [
        'first_10'=>'rocket','perfect'=>'star','answers_100'=>'book','top_10'=>'trophy','streak_7'=>'fire'
    ];
    return ui_icon($map[$code] ?? 'medal', $class);
}

function initial(string $name): string { return mb_substr(trim($name),0,1); }
function clean_name_ok(string $name): bool {
    $bad=['كس','شرموط','قحبة','قحب','نيك','منيك','عاهر','عاهرة','زنا','خرا','كلب','حمار','fuck','shit','bitch','porn','sex','nigger'];
    $n=preg_replace('/\s+/u',' ',trim($name)) ?? trim($name);
    $n=function_exists('mb_strtolower') ? mb_strtolower($n,'UTF-8') : strtolower($n);
    $len=function_exists('mb_strlen') ? mb_strlen($n,'UTF-8') : strlen($n);
    if($len<6 || $len>150) return false;
    if(preg_match('/https?:\/\/|www\.|@/iu',$n)) return false;
    foreach($bad as $w){
        $found=function_exists('mb_strpos') ? mb_strpos($n,$w,0,'UTF-8') : strpos($n,$w);
        if($found!==false) return false;
    }
    return true;
}
function ensure_stats(int $userId): void { db()->prepare("INSERT IGNORE INTO user_stats(user_id) VALUES(?)")->execute([$userId]); }
function grant_xp(int $userId,int $xp,string $reason='نشاط تعليمي'): void {
    ensure_stats($userId);
    $q=db()->prepare("UPDATE user_stats SET xp=xp+?,updated_at=NOW() WHERE user_id=?");
    $q->execute([$xp,$userId]);
    $q2=db()->prepare("SELECT xp FROM user_stats WHERE user_id=?");
    $q2->execute([$userId]);
    $newXp=(int)$q2->fetchColumn();
    $level=max(1,(int)floor($newXp/100)+1);
    db()->prepare("UPDATE user_stats SET level=? WHERE user_id=?")->execute([$level,$userId]);
    try { db()->prepare("INSERT INTO xp_events(user_id,amount,reason) VALUES(?,?,?)")->execute([$userId,$xp,$reason]); } catch(Throwable $e) { error_log('xp event: '.$e->getMessage()); }
    check_and_grant_achievements($userId);
}

// يفحص شروط الإنجازات البسيطة ويمنحها تلقائياً عند تحقّقها (يُستدعى بعد كل نشاط يمنح XP)
function check_and_grant_achievements(int $userId): void {
    try {
        $q=db()->prepare("SELECT total_answers,exams_completed,streak FROM user_stats WHERE user_id=?");
        $q->execute([$userId]);
        $stats=$q->fetch();
        if(!$stats) return;

        $earn=function(string $code) use ($userId) {
            $a=db()->prepare("SELECT id FROM achievements WHERE code=?");
            $a->execute([$code]);
            $id=$a->fetchColumn();
            if(!$id) return;
            $ins=db()->prepare("INSERT IGNORE INTO user_achievements(user_id,achievement_id) VALUES(?,?)");
            $ins->execute([$userId,$id]);
            if($ins->rowCount()>0){
                $t=db()->prepare("SELECT title FROM achievements WHERE id=?");
                $t->execute([$id]);
                notify_user($userId,'🏅 إنجاز جديد',(string)$t->fetchColumn());
            }
        };

        if((int)$stats['total_answers']>=10) $earn('first_10');
        if((int)$stats['total_answers']>=100) $earn('answers_100');
        if((int)$stats['streak']>=7) $earn('streak_7');

        $season=active_season();
        if($season){
            $top=db()->prepare("SELECT u.id FROM users u LEFT JOIN xp_events x ON x.user_id=u.id AND x.created_at BETWEEN ? AND ? WHERE u.role='student' AND u.grade_id=(SELECT grade_id FROM users WHERE id=?) GROUP BY u.id ORDER BY COALESCE(SUM(x.amount),0) DESC,u.id ASC LIMIT 10");
            $top->execute([$season['start_at'],$season['end_at'],$userId]);
            if(in_array($userId,array_map('intval',$top->fetchAll(PDO::FETCH_COLUMN)),true)) $earn('top_10');
        }

        $perfect=db()->prepare("SELECT COUNT(*) FROM exam_attempts WHERE user_id=? AND completed_at IS NOT NULL AND total_questions>0 AND correct=total_questions");
        $perfect->execute([$userId]);
        if((int)$perfect->fetchColumn()>0) $earn('perfect');
    } catch(Throwable $e) { error_log('achievements: '.$e->getMessage()); }
}

function notify_user(int $userId,string $title,string $body): void {
    try {
        $pdo=db();
        $stmt=$pdo->prepare("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)");
        $stmt->execute([$userId,$title,$body]);
        $notificationId=(int)$pdo->lastInsertId();
        try {require_once __DIR__.'/android_fcm.php';android_fcm_schedule($userId);}
        catch(Throwable $e){error_log('Android push enqueue deferred');}
        // Web Push يعمل كـ tickle آمن: التفاصيل تبقى داخل المنصة، والإشعار يوقظ الجهاز فقط.
        try {
            require_once __DIR__.'/push.php';
            push_enqueue($userId,$notificationId>0?$notificationId:null);
        } catch(Throwable $e) { error_log('push notify enqueue: '.$e->getMessage()); }
    } catch(Throwable $e) { error_log('notify user: '.$e->getMessage()); }
}

function active_season(): ?array {
    try { $q=db()->query("SELECT * FROM seasons WHERE status='active' AND start_at<=NOW() AND end_at>=NOW() ORDER BY id DESC LIMIT 1"); return $q->fetch() ?: null; } catch(Throwable $e) { return null; }
}

function season_xp(int $userId,int $seasonId): int {
    $q=db()->prepare("SELECT COALESCE(SUM(amount),0) FROM xp_events x JOIN seasons s ON s.id=? WHERE x.user_id=? AND x.created_at BETWEEN s.start_at AND s.end_at"); $q->execute([$seasonId,$userId]); return (int)$q->fetchColumn();
}

function recalc_stats(int $userId): void {
    ensure_stats($userId); $q=db()->prepare("SELECT COUNT(*) total,COALESCE(SUM(is_correct),0) correct FROM answer_events WHERE user_id=?");$q->execute([$userId]);$r=$q->fetch();
    $q2=db()->prepare("SELECT COUNT(*) FROM exam_attempts WHERE user_id=? AND completed_at IS NOT NULL");$q2->execute([$userId]);$ex=(int)$q2->fetchColumn();
    $q3=db()->prepare("SELECT xp FROM user_stats WHERE user_id=?");$q3->execute([$userId]);$xp=(int)$q3->fetchColumn();
    $level=max(1,(int)floor($xp/100)+1); $q4=db()->prepare("UPDATE user_stats SET total_answers=?,correct_answers=?,exams_completed=?,level=?,updated_at=NOW() WHERE user_id=?");$q4->execute([(int)$r['total'],(int)$r['correct'],$ex,$level,$userId]);
}


/**
 * محرك التصحيح العربي المرن.
 * الهدف: عدم ظلم الطالب بسبب التشكيل، الهمزات، علامات الترقيم، اختلافات إملائية بسيطة،
 * أو اختلاف ترتيب/صياغة الكلمات مع بقاء العناصر الأساسية للإجابة.
 * الحالات القريبة التي تجتاز الحد الآمن تُحتسب صحيحة لمصلحة الطالب.
 */
function arabic_grade_normalize(string $text): string {
    $text = html_entity_decode($text, ENT_QUOTES | ENT_HTML5, 'UTF-8');
    $text = trim($text);
    if ($text === '') return '';
    // حذف التشكيل، علامات المصحف، والتطويل.
    $text = preg_replace('/[\x{0610}-\x{061A}\x{064B}-\x{065F}\x{0670}\x{06D6}-\x{06ED}\x{0640}]/u', '', $text) ?? $text;
    $text = strtr($text, [
        'أ'=>'ا','إ'=>'ا','آ'=>'ا','ٱ'=>'ا','ى'=>'ي','ؤ'=>'و','ئ'=>'ي','ة'=>'ه',
        '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
        '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
    ]);
    $text = mb_strtolower($text, 'UTF-8');
    // 1997م و1997 م و1997 ميلادي كلها تعامل كتاريخ واحد.
    $text = preg_replace('/(?<=\d)\s*(?:م|ميلادي(?:ه)?)\b/u', '', $text) ?? $text;
    $text = preg_replace('/[^\p{L}\p{N}]+/u', ' ', $text) ?? $text;
    return trim(preg_replace('/\s+/u', ' ', $text) ?? $text);
}
function arabic_grade_token_canon(string $token): string {
    $token = arabic_grade_normalize($token);
    if ($token === '' || str_contains($token, ' ')) return str_replace(' ', '', $token);
    $len = mb_strlen($token, 'UTF-8');
    // فصل الواو/الفاء المتصلة عندما تكون حرف عطف، دون المساس بالكلمات القصيرة مثل "واو".
    if ($len >= 5 && in_array(mb_substr($token,0,1,'UTF-8'), ['و','ف'], true)) {
        $token = mb_substr($token,1,null,'UTF-8');
        $len = mb_strlen($token,'UTF-8');
    }
    foreach (['بال','كال','فال','وال'] as $p) {
        if (str_starts_with($token,$p) && mb_strlen($token,'UTF-8') > mb_strlen($p,'UTF-8')+2) {
            $token = mb_substr($token,1,null,'UTF-8');
            break;
        }
    }
    if (str_starts_with($token,'لل') && mb_strlen($token,'UTF-8') >= 5) $token='ال'.mb_substr($token,2,null,'UTF-8');
    if (str_starts_with($token,'ال') && mb_strlen($token,'UTF-8') >= 5) $token=mb_substr($token,2,null,'UTF-8');

    // مرادفات قصيرة شائعة لا ينبغي أن تُسقط إجابة الطالب.
    $aliases=[
        'عام'=>'سنه','سنة'=>'سنه','سنـه'=>'سنه','سنه'=>'سنه',
        'ميلادي'=>'سنه','ميلاديه'=>'سنه',
        'توفى'=>'توفي','وفاته'=>'توفي','وفاه'=>'توفي',
        'مولده'=>'ولد','ولادته'=>'ولد','ولد'=>'ولد',
    ];
    return $aliases[$token] ?? $token;
}
function arabic_grade_tokens(string $text, bool $significant=true): array {
    $n=arabic_grade_normalize($text);
    if($n==='') return [];
    $stop=['في','من','علي','عن','الي','مع','ثم','او','هو','هي','هذا','هذه','ذلك','تلك','كما','فقط','ايضا','الذي','التي','الذين','وهو','وهي','يكون','تكون','كانت','كان','ورد','وارده','وارد','اجابه','جواب'];
    $out=[];
    foreach(preg_split('/\s+/u',$n,-1,PREG_SPLIT_NO_EMPTY)?:[] as $t){
        $t=arabic_grade_token_canon($t);
        if($t==='') continue;
        if($significant && (in_array($t,$stop,true) || mb_strlen($t,'UTF-8')<2)) continue;
        $out[]=$t;
    }
    return array_values(array_unique($out));
}
/**
 * عناصر المعلومة الجوهرية في جواب قصير. نحذف ألفاظ الصياغة التي يمكن للطالب
 * الاستغناء عنها عندما يجيب مباشرة بالمكان/السنة/الاسم، مع إبقاء الحقائق نفسها.
 */
function arabic_grade_core_tokens(string $text): array {
    $tokens=arabic_grade_tokens($text,true);
    if(!$tokens) return [];
    $hasNumber=false;foreach($tokens as $t){if(preg_match('/^\d+$/u',$t)){$hasNumber=true;break;}}
    $scaffold=['توفي','ولد','نشر','نشرت','صدر','اصدر','اصدرت','يقع','تقع','حدث','حدثت','حصل','حصلت','اسم','عنوان','سمي','تسمي','يسمي'];
    $out=[];
    foreach($tokens as $t){
        if(in_array($t,$scaffold,true)) continue;
        if($hasNumber && $t==='سنه') continue;
        $out[]=$t;
    }
    // لا نجعل الحذف يفرغ الإجابة؛ نرجع للأصل عند الحاجة.
    return count($out)>=1 ? array_values(array_unique($out)) : $tokens;
}
function arabic_grade_bigrams(string $s): array {
    $s=str_replace(' ','',arabic_grade_normalize($s));$len=mb_strlen($s,'UTF-8');
    if($len<2) return $s===''?[]:[$s];
    $out=[];for($i=0;$i<$len-1;$i++)$out[]=mb_substr($s,$i,2,'UTF-8');return $out;
}
function arabic_grade_dice(string $a,string $b): float {
    $A=arabic_grade_bigrams($a);$B=arabic_grade_bigrams($b);if(!$A||!$B)return 0.0;
    $counts=[];foreach($A as $x)$counts[$x]=($counts[$x]??0)+1;$hit=0;
    foreach($B as $x){if(($counts[$x]??0)>0){$hit++;$counts[$x]--;}}
    return (2.0*$hit)/(count($A)+count($B));
}
function arabic_grade_token_similarity(string $a,string $b): float {
    $a=arabic_grade_token_canon($a);$b=arabic_grade_token_canon($b);if($a===$b)return 1.0;
    $la=mb_strlen($a,'UTF-8');$lb=mb_strlen($b,'UTF-8');if(min($la,$lb)<3)return 0.0;
    if(min($la,$lb)>=4 && (str_starts_with($a,$b)||str_starts_with($b,$a)) && abs($la-$lb)<=2)return .90;
    return arabic_grade_dice($a,$b);
}
function arabic_grade_match_coverage(array $expected,array $given): array {
    if(!$expected)return ['coverage'=>0.0,'matched'=>0];
    $used=[];$matched=0;
    foreach($expected as $e){$best=-1;$bestScore=0.0;foreach($given as $i=>$g){if(isset($used[$i]))continue;$sim=arabic_grade_token_similarity($e,$g);$need=(mb_strlen($e,'UTF-8')<=3||mb_strlen($g,'UTF-8')<=3)?.96:.79;if($sim>=$need&&$sim>$bestScore){$best=$i;$bestScore=$sim;}}if($best>=0){$used[$best]=true;$matched++;}}
    return ['coverage'=>$matched/count($expected),'matched'=>$matched];
}
function arabic_grade_candidates(string $correct): array {
    $candidates=[$correct];
    foreach(preg_split('/\s*\|\|\s*|\s*\.\.\s*او\s*\.\.\s*|\s+او\s+/u',arabic_grade_normalize($correct),-1,PREG_SPLIT_NO_EMPTY)?:[] as $part){
        if(count(arabic_grade_tokens($part,true))>=2)$candidates[]=$part;
    }
    $seen=[];$out=[];foreach($candidates as $c){$n=arabic_grade_normalize($c);if($n!==''&&!isset($seen[$n])){$seen[$n]=1;$out[]=$c;}}return $out;
}
function arabic_grade_event_conflict(string $given,string $correct): bool {
    $g=arabic_grade_tokens($given,true);$c=arabic_grade_tokens($correct,true);
    $groups=[['ولد','توفي']];
    foreach($groups as $grp){$cg=array_values(array_intersect($c,$grp));$gg=array_values(array_intersect($g,$grp));if($cg&&$gg&&$cg[0]!==$gg[0])return true;}
    return false;
}
function grade_arabic_answer(string $given,string $correct,string $type='text',string $questionText=''): array {
    $given=trim($given);$correct=trim($correct);
    if($type==='true_false'){
        $tf=static function(string $v): string {
            $n=arabic_grade_normalize($v);
            if(in_array($n,['صح','صحيح','صحيحه','true','1','yes'],true))return 'true';
            if(in_array($n,['خطا','خطاء','غير صحيح','false','0','no'],true))return 'false';
            return $n;
        };
        $g=$tf($given);$c=$tf($correct);
        if($g!==''&&$c!==''&&$g===$c)return ['is_correct'=>true,'status'=>'correct','score'=>100.0,'message'=>'إجابة صحيحة'];
        return ['is_correct'=>false,'status'=>'incorrect','score'=>0.0,'message'=>'إجابة غير صحيحة'];
    }
    if($given===''||$correct==='')return ['is_correct'=>false,'status'=>'incorrect','score'=>0.0,'message'=>'الإجابة غير مكتملة'];
    // A Quran transcription MUST match every word in order. Fuzzy word coverage is unsafe here.
    if($questionText!=='' && murshid_quran_transcription_prompt($questionText)){
        $ok=murshid_quran_same($given,$correct);
        return ['is_correct'=>$ok,'status'=>$ok?'correct':'incorrect','score'=>$ok?100.0:0.0,
            'message'=>$ok?'إجابة صحيحة':'النص القرآني غير مطابق بالكامل؛ راجع ترتيب الكلمات والكلمات الناقصة'];
    }
    // Mathematical expressions must be compared semantically rather than as Arabic prose.
    if(murshid_is_math_question($correct,$questionText,$type)){
        $equivalent=murshid_math_equivalent($given,$correct);
        if($equivalent!==null){
            return ['is_correct'=>$equivalent,'status'=>$equivalent?'correct':'incorrect',
                'score'=>$equivalent?100.0:0.0,'message'=>$equivalent?'إجابة صحيحة':'إجابة رياضية غير مكافئة'];
        }
    }
    $ng=arabic_grade_normalize($given);$nc=arabic_grade_normalize($correct);
    if($ng===$nc)return ['is_correct'=>true,'status'=>'correct','score'=>100.0,'message'=>'إجابة صحيحة'];

    if(in_array($type,['mcq','true_false'],true))return ['is_correct'=>false,'status'=>'incorrect','score'=>0.0,'message'=>'إجابة غير صحيحة'];
    if(arabic_grade_event_conflict($given,$correct))return ['is_correct'=>false,'status'=>'incorrect','score'=>0.0,'message'=>'إجابة غير صحيحة'];

    $best=['is_correct'=>false,'status'=>'incorrect','score'=>0.0,'message'=>'إجابة غير صحيحة'];
    foreach(arabic_grade_candidates($correct) as $candidate){
        $et=arabic_grade_tokens($candidate,true);$gt=arabic_grade_tokens($given,true);
        if(!$et){$et=arabic_grade_tokens($candidate,false);}if(!$gt){$gt=arabic_grade_tokens($given,false);}
        $m1=arabic_grade_match_coverage($et,$gt);$m2=arabic_grade_match_coverage($gt,$et);
        $coverage=$m1['coverage'];$precision=$m2['coverage'];$dice=arabic_grade_dice($given,$candidate);
        $score=max(0.0,min(1.0,.64*$coverage+.14*$precision+.22*$dice));
        $n=count($et);$minMatches=$n<=2?1:2;
        if($n<=2){$accepted=($m1['matched']>=$minMatches && $coverage>=.85 && ($dice>=.55||$precision>=.70));}
        elseif($n<=5){$accepted=($m1['matched']>=$minMatches && $coverage>=.72 && $score>=.66);}
        else{$accepted=($m1['matched']>=$minMatches && $coverage>=.64 && $score>=.61);}

        // مسار "الجواب المباشر": مثال "في دمشق سنة 1997" مقابل
        // "توفي في دمشق عام 1997م". نتحقق من جميع الحقائق الجوهرية ولا نلزم الطالب
        // بإعادة فعل السؤال أو لفظ عام/سنة إذا كان التاريخ نفسه موجوداً.
        $coreE=arabic_grade_core_tokens($candidate);$coreG=arabic_grade_core_tokens($given);
        $core=arabic_grade_match_coverage($coreE,$coreG);
        $corePrecision=arabic_grade_match_coverage($coreG,$coreE);
        $coreAccepted=false;
        if(count($coreE)>=2){
            $coreAccepted=($core['coverage']>=.90 && $core['matched']>=2 && $corePrecision['coverage']>=.66);
        }elseif(count($coreE)===1){
            $coreAccepted=($core['coverage']>=1.0 && $corePrecision['coverage']>=.50);
        }
        if($coreAccepted){$accepted=true;$score=max($score,.86);}

        $strong=$accepted && ($coverage>=.88 || $score>=.90);
        $res=['is_correct'=>$accepted,'status'=>$accepted?($strong?'correct':'accepted'):'incorrect','score'=>round($score*100,1),'message'=>$accepted?($strong?'إجابة صحيحة':'إجابة مقبولة — العناصر المطلوبة صحيحة'):'إجابة غير صحيحة'];
        if($res['score']>$best['score'] || ($res['is_correct']&&!$best['is_correct']))$best=$res;
    }
    return $best;
}
function is_arabic_answer_correct(string $given,string $correct,string $type='text',string $questionText=''): bool {
    return (bool)(grade_arabic_answer($given,$correct,$type,$questionText)['is_correct']??false);
}

/**
 * اسم الموضوع الذي يراه الطالب. قواعد البيانات القديمة قد تحتفظ باسم عام مثل
 * "الفصل الثاني"؛ نحوله للعنوان المنهجي الحقيقي من دون تغيير رقم الفصل أو السؤال.
 */
function public_topic_name(string $subjectName, string $chapterName, int $sortOrder=0, string $gradeName=''): string {
    return topic_display_name($subjectName, $chapterName, $gradeName);
}

/**
 * بيانات المصدر محفوظة للإدارة، لكن واجهة الطالب لا تعرض أسماء أصحاب المصادر التعليمية.
 * سعد خلف الحمراني يظهر فقط في بطاقة الأدب المخصصة له.
 */
function public_question_ministerial_label(array $question): array {
    // واجهة الطالب تعرض كل أسئلة المنصة بوسم موحّد حسب طلب المنتج.
    // لا نغيّر بيانات التحقق الداخلية أو مصدر السؤال في قاعدة البيانات.
    return ['label' => 'وزاري', 'kind' => 'official'];
}

/**
 * نص السؤال الظاهر للطالب فقط.
 * يخفي البادئات التحريرية/التدريبية الزائدة من بداية السؤال من دون تعديل أصل السؤال في قاعدة البيانات.
 */
function public_question_display_text(?string $text): string {
    $text=murshid_without_inline_hints((string)$text);
    if($text==='') return '';

    // صيغة تعريف المصطلح تحتاج إبقاء فعل السؤال بعد حذف ترقيم التحرير.
    if(preg_match('/^\s*عرّف\s+المصطلح\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*(.+)$/u',$text,$m)){
        $tail=trim((string)$m[1]);
        if($tail!=='') return 'عرّف '.$tail;
    }
    // «أكمل المعنى الوزاري…» تظهر كسؤال معنى مباشر.
    if(preg_match('/^\s*أكمل\s+المعنى\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*المقصود\s+بـ?[«"“](.+?)[»"”]\s+هو\s*_+[.。]?\s*$/u',$text,$m)){
        return 'ما معنى «'.trim((string)$m[1]).'»؟';
    }

    $patterns=[
        '/^\s*استرجاع\s+وزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*وزاري(?:\s+[\p{N}٠-٩]{2,4})?(?:\s*[\/\-–—]\s*[^:：\n]{1,48})?\s*[:：]\s*/u',
        '/^\s*(?:سؤال\s+)?(?:تدريب|تدريبي|تدريبية)(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*(?:سؤال\s+)?مراجعة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*اختيار\s+تحليلي(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*إجابة\s+مركزة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*تحقق\s+بصري(?:\s+[\p{N}٠-٩]{1,4})?\s*[\-–—]?\s*(?:v\s*25|v25)?\s*[:：]\s*/iu',
    ];
    foreach($patterns as $pattern){
        $clean=preg_replace($pattern,'',$text,1,$count);
        if($count>0){$text=trim((string)$clean);break;}
    }
    return trim((string)(preg_replace('/[ \t]{2,}/u',' ',$text)??$text));
}

function public_exam_round(?string $round): string {
    $round=trim((string)$round);
    if($round==='') return '';
    $patterns=[
        '/التمهيدي/u',
        '/الدور\s+(?:الأول|الثاني|الثالث)/u',
        '/الدور\s+(?:الاول|الثاني|الثالث)/u',
    ];
    foreach($patterns as $pattern){if(preg_match($pattern,$round,$m)) return $m[0];}
    if(preg_match('/وزاري/u',$round)) return 'وزاري';
    // لا نعرض نصاً غير معروف لأنه قد يكون اسم مدرس/مصدر تعليمي.
    return '';
}

/** مفتاح مبسّط لمقارنة الإجابة والتوضيح مع تجاهل الحركات والترقيم والفروقات الشكلية. */
function public_question_compare_key(?string $text): string {
    $text=html_entity_decode(strip_tags((string)$text),ENT_QUOTES|ENT_HTML5,'UTF-8');
    $text=preg_replace('/[\x{064B}-\x{065F}\x{0670}\x{0640}\x{200C}\x{200D}\x{200E}\x{200F}\x{202A}-\x{202E}]/u','',$text)??$text;
    $text=strtr($text,['أ'=>'ا','إ'=>'ا','آ'=>'ا','ٱ'=>'ا','ى'=>'ي']);
    $text=mb_strtolower($text,'UTF-8');
    $text=preg_replace('/[^\p{L}\p{N}]+/u','',$text)??$text;
    return trim($text);
}

/**
 * النص التوضيحي الظاهر للطالب.
 * - يخفي ملاحظات المصدر الداخلية.
 * - لا يعيد الإجابة نفسها تحت عنوان «التوضيح».
 * - يخفي الصيغ العامة التي لا تضيف معلومة بعد الإجابة.
 */
function public_question_explanation(?string $text, ?string $answer=null): string {
    $text=trim((string)$text);
    if($text==='') return '';
    if(preg_match('/^المصدر\s*:/u',$text)) return '';
    // لا نعرض تلميحات تحريرية عامة على أنها شرح؛ إما شرح فعلي أو لا شيء.
    $genericPatterns=[
        '/^تدريب\s+مشتق\s+من\s+مفهوم\s+موثق\s+في\s+هذا\s+المحور/u',
        '/^تدريب\s+مشتق\s+موثق/u',
        '/^هذا\s+تدريب\s+(?:تحقق\s+)?مشتق/u',
        '/^صيغة\s+تدريب\s+جديدة\s+مشتقة/u',
        '/^راجع\s+الإجابة\s+النموذجية/u',
    ];
    foreach($genericPatterns as $pattern) if(preg_match($pattern,$text)) return '';

    $answer=trim((string)$answer);
    if($answer==='') return $text;
    $answerKey=public_question_compare_key($answer);
    if($answerKey==='') return $text;
    if(public_question_compare_key($text)===$answerKey) return '';

    // أمثلة قديمة: «الإجابة الصحيحة هي: ...» أو «الإجابة المعتمدة للسؤال المرجعي هي «...»».
    $wrapped=preg_replace('/^(?:الإجابة(?:\s+(?:الصحيحة|النموذجية|المعتمدة(?:\s+للسؤال\s+المرجعي)?))?)(?:\s+هي|\s*:)?\s*[«"“”]?/u','',$text)??$text;
    $wrapped=preg_replace('/[»"“”]?[.،؛!؟\s]*(?:هذا\s+تدريب\s+(?:تحقق\s+)?مشتق.*)?$/u','',$wrapped)??$wrapped;
    if(public_question_compare_key($wrapped)===$answerKey) return '';

    return $text;
}

define('FREE_QUESTION_LIMIT', defined('FREE_QUESTION_LIMIT_CONFIG') ? (int)FREE_QUESTION_LIMIT_CONFIG : 15);

function free_answers_used(int $userId): int {
    try {
        $pdo = db();

        // الحساب الجديد يجب أن يبدأ من صفر دائماً. في بعض قواعد البيانات القديمة
        // قد تبقى answer_events مرتبطة برقم مستخدم أُعيد استخدامه بعد حذف حساب سابق.
        // إذا كان users.created_at موجوداً، لا نحسب أي إجابة أقدم من إنشاء الحساب الحالي.
        static $usersHasCreatedAt = null;
        if ($usersHasCreatedAt === null) {
            try {
                $c = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='created_at'");
                $c->execute();
                $usersHasCreatedAt = (bool)$c->fetchColumn();
            } catch (Throwable $e) {
                $usersHasCreatedAt = false;
            }
        }

        if ($usersHasCreatedAt) {
            $q = $pdo->prepare("SELECT COUNT(DISTINCT ae.question_id) FROM answer_events ae JOIN users u ON u.id=ae.user_id WHERE ae.user_id=? AND (u.created_at IS NULL OR ae.created_at>=u.created_at)");
            $q->execute([$userId]);
            return (int)$q->fetchColumn();
        }

        $q=$pdo->prepare("SELECT COUNT(DISTINCT question_id) FROM answer_events WHERE user_id=?");
        $q->execute([$userId]);
        return (int)$q->fetchColumn();
    } catch(Throwable $e){
        error_log('free_answers_used failed: '.$e->getMessage());
        return 0;
    }
}
function free_answers_remaining(int $userId): int {
    if(is_subscribed($userId)) return PHP_INT_MAX;
    return max(0, FREE_QUESTION_LIMIT - free_answers_used($userId));
}
function requires_subscription_for_more_answers(int $userId): bool {
    return !is_subscribed($userId) && free_answers_used($userId) >= FREE_QUESTION_LIMIT;
}
/** return: 1=new answer inserted, 0=already answered, -1=quota blocked.
 *  القفل على صف المستخدم يجعل حد 15 سؤالاً آمناً حتى مع طلبات متزامنة. */
function record_answer_event_with_quota(int $userId,int $questionId,int $chapterId,bool $isCorrect,string $answerText): int {
    $pdo=db();$ownTx=!$pdo->inTransaction();if($ownTx)$pdo->beginTransaction();
    try {
        $lock=$pdo->prepare("SELECT id FROM users WHERE id=? FOR UPDATE");$lock->execute([$userId]);
        if(!$lock->fetchColumn()) throw new RuntimeException('User not found');
        $question=$pdo->prepare("SELECT q.chapter_id FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id JOIN users u ON u.grade_id=s.grade_id WHERE q.id=? AND u.id=? AND c.status=1 AND s.status=1 AND ".topic_live_sql('q')." FOR UPDATE");
        $question->execute([$questionId,$userId]);$actualChapter=(int)$question->fetchColumn();
        if(!$actualChapter) throw new RuntimeException('Question unavailable for student grade');
        $chapterId=$actualChapter;
        $seen=$pdo->prepare("SELECT id FROM answer_events WHERE user_id=? AND question_id=? LIMIT 1");
        $seen->execute([$userId,$questionId]);
        if($seen->fetchColumn()){if($ownTx)$pdo->commit();return 0;}
        if(!is_subscribed($userId)){
            // نعتمد نفس العداد المستخدم في الواجهة حتى لا يُطلب الاشتراك قبل 15
            // ولا يمكن قبول السؤال رقم 16 حتى مع طلبات متزامنة.
            if(free_answers_used($userId)>=FREE_QUESTION_LIMIT){if($ownTx)$pdo->commit();return -1;}
        }
        try{
            $ins=$pdo->prepare("INSERT INTO answer_events(user_id,question_id,chapter_id,is_correct,answer_text) VALUES(?,?,?,?,?)");
            $ins->execute([$userId,$questionId,$chapterId,$isCorrect?1:0,$answerText]);
        }catch(PDOException $e){
            if((string)$e->getCode()==='23000'){if($ownTx)$pdo->commit();return 0;}
            throw $e;
        }
        if($ownTx)$pdo->commit();return 1;
    }catch(Throwable $e){if($ownTx&&$pdo->inTransaction())$pdo->rollBack();throw $e;}
}

function audit_log(string $action, ?string $entityType=null, ?string $entityId=null): void {
    try {
        $uid = !empty($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : null;
        $ip = function_exists('murshid_client_ip') ? murshid_client_ip() : substr((string)($_SERVER['REMOTE_ADDR'] ?? ''),0,45);
        $ua = substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''),0,255);
        db()->prepare("INSERT INTO audit_logs(user_id,action,entity_type,entity_id,ip_address,user_agent) VALUES(?,?,?,?,?,?)")
            ->execute([$uid,$action,$entityType,$entityId,$ip,$ua]);
    } catch (Throwable $e) { error_log('audit_log: '.$e->getMessage()); }
}
function daily_streak_touch(int $userId): void {
    try {
        ensure_stats($userId);
        $q=db()->prepare("SELECT last_active_date,streak FROM user_stats WHERE user_id=?"); $q->execute([$userId]); $r=$q->fetch();
        $today=new DateTimeImmutable('today'); $last=!empty($r['last_active_date'])?new DateTimeImmutable($r['last_active_date']):null;
        $streak=(int)($r['streak']??0);
        if(!$last) $streak=1;
        elseif($last->format('Y-m-d')===$today->format('Y-m-d')) return;
        elseif($last->modify('+1 day')->format('Y-m-d')===$today->format('Y-m-d')) $streak++;
        else $streak=1;
        db()->prepare("UPDATE user_stats SET streak=?,last_active_date=?,updated_at=NOW() WHERE user_id=?")->execute([$streak,$today->format('Y-m-d'),$userId]);
        if(in_array($streak,[3,7,30],true)) notify_user($userId,'🔥 سلسلة دراسة جديدة','حافظت على سلسلة '.$streak.' أيام. استمر!');
    } catch(Throwable $e) { error_log('streak: '.$e->getMessage()); }
}
function get_or_create_daily_challenge(): ?array {
    try {
        $today=date('Y-m-d'); $q=db()->prepare("SELECT * FROM daily_challenges WHERE challenge_date=? LIMIT 1"); $q->execute([$today]); $row=$q->fetch();
        if($row) return $row;
        $pdo=db();
        $active=topic_live_sql('q');
        $qs=$pdo->query("SELECT q.id FROM questions q WHERE {$active} ORDER BY q.id DESC LIMIT 60")->fetchAll(PDO::FETCH_COLUMN);
        if(!$qs) return null;
        shuffle($qs); $qs=array_slice($qs,0,10);
        $ins=db()->prepare("INSERT INTO daily_challenges(challenge_date,question_ids_json,xp_reward) VALUES(?,?,100)"); $ins->execute([$today,json_encode(array_map('intval',$qs))]);
        $q->execute([$today]); return $q->fetch() ?: null;
    } catch(Throwable $e) { return null; }
}
function study_minutes(int $userId,int $days=7): int {
    try { $q=db()->prepare("SELECT COALESCE(SUM(minutes),0) FROM study_sessions WHERE user_id=? AND created_at>=DATE_SUB(NOW(),INTERVAL ? DAY)"); $q->execute([$userId,$days]); return (int)$q->fetchColumn(); } catch(Throwable $e){return 0;}
}

function maintenance_tools_allowed(): bool {
    return defined('ENABLE_MAINTENANCE_TOOLS') && ENABLE_MAINTENANCE_TOOLS === true;
}
function require_maintenance_tools(): void {
    if (maintenance_tools_allowed()) return;
    http_response_code(404);
    exit('أداة الصيانة مغلقة. فعّل ENABLE_MAINTENANCE_TOOLS مؤقتاً من config.php عند الحاجة فقط.');
}

/*
 * v43: تم إيقاف أي استيراد تلقائي للمحتوى أثناء طلبات الطلاب.
 * استيراد/ترقية المحتوى عملية إدارية صريحة فقط حتى لا تستهلك موارد الاستضافة.
 */

// v46 content normalization: direct question prompts + 50/50 Islamic meanings.
require_once __DIR__.'/question_prompt_cleanup_v46.php';
try { murshid_apply_question_prompt_cleanup_v46_once(); } catch(Throwable $e) { error_log('question prompt auto-normalize: '.$e->getMessage()); }

// v46.1: إزالة التوضيح المكرر عندما يكون مطابقاً للإجابة.
require_once __DIR__.'/question_explanation_cleanup_v46.php';
try { murshid_apply_explanation_cleanup_v46_once(); } catch(Throwable $e) { error_log('question explanation auto-cleanup: '.$e->getMessage()); }

// v47: تنظيف جذري لنصوص التحقق الداخلية والتكرار وصيغ الصح/الخطأ.
require_once __DIR__.'/question_quality_cleanup_v47.php';
try { murshid_apply_question_quality_cleanup_v47_once(); } catch(Throwable $e) { error_log('question quality v47: '.$e->getMessage()); }

// v48: ربط نوع السؤال وخياراته وإجابته ومسار التصحيح بطبقة سلامة واحدة.
require_once __DIR__.'/question_integrity_v48.php';
try { murshid_apply_question_integrity_v48_once(); } catch(Throwable $e) { error_log('question integrity v48: '.$e->getMessage()); }
