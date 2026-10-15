<?php
declare(strict_types=1);

// The phone registration API and this endpoint share the same student session.
// Do not call current_user()/remember_login_restore() before reading its pending
// identity: an older remembered account must not replace a pending registration.
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
header('X-Content-Type-Options: nosniff');

try {
    $method = (string)($_SERVER['REQUEST_METHOD'] ?? 'GET');
    if (!in_array($method, ['GET', 'POST'], true)) {
        header('Allow: GET, POST');
        json_response(['ok'=>false, 'message'=>'طريقة الطلب غير مدعومة.'], 405);
    }

    $user = phone_verification_pending_user();
    if (!$user) {
        json_response([
            'ok'=>false, 'verification_session_expired'=>true, 'login_required'=>true,
            'message'=>'انتهت جلسة تأكيد الهاتف. ارجع وسجّل الدخول بنفس الرقم وكلمة المرور، ثم أكمل التأكيد دون إنشاء حساب جديد.',
        ], 409);
    }
    if (!empty($user['phone_verified_at'])) {
        phone_verification_clear_pending();
        json_response(['ok'=>false, 'phone_verified'=>true, 'login_required'=>true,
            'message'=>'رقم الهاتف مؤكد مسبقاً. ارجع وسجّل الدخول بنفس بيانات الحساب.'], 409);
    }

    if ($method === 'GET') {
        json_response([
            'ok'=>true, 'csrf'=>csrf_token(), 'pending'=>true,
            'masked_phone'=>phone_verification_mask((string)$user['phone']),
        ] + phone_verification_status((int)$user['id']));
    }

    $raw = file_get_contents('php://input');
    $data = json_decode(is_string($raw) ? $raw : '', true);
    if (!is_array($data)) json_response(['ok'=>false, 'message'=>'صيغة الطلب غير صالحة.'], 400);
    if (!verify_csrf(is_string($data['csrf'] ?? null) ? $data['csrf'] : null)) {
        json_response(['ok'=>false, 'csrf_expired'=>true,
            'message'=>'انتهت صلاحية الطلب. اضغط تأكيد الحساب مجدداً لتجديد حماية الطلب.'], 419);
    }
    $action = (string)($data['action'] ?? '');
    if (!in_array($action, ['verify', 'resend'], true)) {
        json_response(['ok'=>false, 'message'=>'إجراء التحقق غير صالح.'], 422);
    }

    // The account and destination come exclusively from the pending session.
    // A supplied user_id/phone cannot select another student's verification.
    if ($action === 'resend') {
        $result = phone_verification_send_code((int)$user['id'], (string)$user['phone'], true);
        $result += phone_verification_status((int)$user['id']);
        $retryAfter = max(0, (int)($result['retry_after'] ?? 0));
        if (empty($result['ok']) && $retryAfter > 0) header('Retry-After: ' . $retryAfter);
        json_response($result, !empty($result['ok']) ? 200 : ($retryAfter > 0 ? 429 : 503));
    }

    $result = phone_verification_verify_code((int)$user['id'], (string)($data['code'] ?? ''));
    if (empty($result['ok'])) json_response($result, 422);

    // Same protected-session finalization as the existing web phone flow.
    session_regenerate_id(true);
    $claim = single_session_claim((int)$user['id']);
    if (empty($claim['ok'])) {
        phone_verification_clear_pending();
        $inUse = !empty($claim['account_in_use']);
        json_response(['ok'=>false, 'phone_verified'=>true, 'login_required'=>true,
            'account_in_use'=>$inUse,
            'message'=>$inUse
                ? 'تم تأكيد الرقم، لكن الحساب مسجل الدخول على جهاز آخر. سجّل الخروج منه ثم ادخل هنا.'
                : 'تم تأكيد الرقم. تعذر فتح جلسة محمية حالياً؛ ارجع وسجّل الدخول بنفس بيانات الحساب.',
        ], $inUse ? 409 : 503);
    }
    $_SESSION['user_id'] = (int)$user['id'];
    $_SESSION['_account_session_token'] = (string)$claim['token'];
    $_SESSION['_account_session_touch'] = time();
    $_SESSION['_auth_started_at'] = time();
    $_SESSION['_auth_last_at'] = time();
    if (!empty($_SESSION['_pending_remember_me'])
        && in_array((string)($user['role'] ?? 'student'), ['student', 'teacher'], true)) {
        if (remember_login_issue((int)$user['id'])) $_SESSION['_remembered_login'] = 1;
    }
    unset($_SESSION['_pending_remember_me']);
    phone_verification_clear_pending();
    try { audit_log('تأكيد رقم الهاتف وتسجيل الدخول', 'user', (string)$user['id']); }
    catch (Throwable $e) { error_log('phone API verify audit failed'); }
    json_response(['ok'=>true, 'verified'=>true, 'message'=>'تم تأكيد رقم الهاتف بنجاح.']);
} catch (Throwable $e) {
    // Never log the request body, the OTP, the CSRF token or service keys.
    error_log('phone verification API failed: ' . get_class($e));
    json_response(['ok'=>false, 'message'=>'تعذر إكمال تأكيد الهاتف حالياً. حاول بعد قليل.'], 503);
}
