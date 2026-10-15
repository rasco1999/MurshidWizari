<?php
declare(strict_types=1);
// Disposable contract fixture only. Never include in the deployment ZIP.
session_name('fixture_phone');
session_start();
function json_response(array $data, int $status=200): never {
    http_response_code($status); header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE); exit;
}
function current_user(): ?array { throw new RuntimeException('Remembered accounts must not restore during verification'); }
function csrf_token(): string { return $_SESSION['csrf'] ??= bin2hex(random_bytes(32)); }
function verify_csrf(?string $token): bool {
    return is_string($token) && $token !== '' && hash_equals($_SESSION['csrf'] ?? '', $token);
}
function phone_verification_pending_user(): ?array {
    if (empty($_SESSION['pending']) || !empty($_SESSION['expired'])) return null;
    return ['id'=>900001, 'phone'=>'07701234567', 'role'=>'student',
        'phone_verified_at'=>!empty($_SESSION['verified']) ? '2026-10-15 00:00:00' : null];
}
function phone_verification_clear_pending(): void { unset($_SESSION['pending']); }
function phone_verification_mask(string $phone): string { return '0770****567'; }
function phone_verification_status(int $id): array {
    return ['remaining_attempts'=>5-($_SESSION['attempts'] ?? 0), 'retry_after'=>$_SESSION['cooldown'] ?? 0,
        'verified'=>!empty($_SESSION['verified'])];
}
function phone_verification_send_code(int $id, string $phone, bool $resend): array {
    if (!empty($_SESSION['cooldown'])) return ['ok'=>false, 'retry_after'=>60, 'message'=>'انتظر 60 ثانية.'];
    $_SESSION['sends']=($_SESSION['sends'] ?? 0)+1;
    $_SESSION['target_id']=$id; $_SESSION['target_phone']=$phone;
    return ['ok'=>true, 'retry_after'=>60, 'message'=>'تم طلب رمز جديد.'];
}
function phone_verification_verify_code(int $id, string $code): array {
    $_SESSION['attempts']=($_SESSION['attempts'] ?? 0)+1; $_SESSION['target_id']=$id;
    if (!empty($_SESSION['otp_expired'])) return ['ok'=>false, 'expired'=>true, 'message'=>'انتهت صلاحية الرمز.'];
    if ($code !== '123456') return ['ok'=>false, 'remaining_attempts'=>4, 'message'=>'رمز التحقق غير صحيح.'];
    $_SESSION['verified']=true;
    return ['ok'=>true];
}
function single_session_claim(int $id): array {
    if (!empty($_SESSION['guard_busy'])) return ['ok'=>false, 'account_in_use'=>true];
    if (!empty($_SESSION['guard_error'])) return ['ok'=>false, 'guard_error'=>true];
    return ['ok'=>true, 'token'=>str_repeat('a',64)];
}
function remember_login_issue(int $id): bool { $_SESSION['remember_issued']=true; return true; }
function audit_log(string $action, string $entity, string $id): void {}
