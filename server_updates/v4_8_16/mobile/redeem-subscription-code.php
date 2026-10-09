<?php
declare(strict_types=1);

// Install as public_html/mobile/redeem-subscription-code.php.
// common.php supplies the existing authenticated student session, PDO, CSRF,
// server-side rate limiting and json_response(). Do not accept a user_id from the client.
require_once __DIR__ . '/common.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_response(['ok' => false, 'message' => 'طريقة الطلب غير مدعومة.'], 405);
}

$student = mobile_student();
$userId = (int) ($student['id'] ?? 0);
if ($userId <= 0) {
    json_response(['ok' => false, 'message' => 'يجب تسجيل الدخول أولاً.'], 401);
}

$body = json_decode((string) file_get_contents('php://input'), true);
$csrf = is_array($body) ? ($body['csrf'] ?? null) : null;
if (!is_string($csrf) || !verify_csrf($csrf)) {
    json_response(['ok' => false, 'message' => 'انتهت صلاحية الطلب. حدّث حسابك وحاول مجدداً.'], 419);
}

// A database-backed limit shared across devices. Never include entered codes in logs.
if (!rate_limit('student_subscription_code_redeem', 6, 3600)) {
    json_response(['ok' => false, 'message' => 'محاولات كثيرة. حاول مجدداً بعد ساعة.'], 429);
}

$raw = $body['code'] ?? '';
if (!is_string($raw) || strlen($raw) > 80) {
    json_response(['ok' => false, 'message' => 'الكود غير صالح أو انتهت صلاحيته.'], 422);
}
$normalized = strtoupper(preg_replace('/[\s-]+/u', '', trim($raw)) ?? '');
if (!preg_match('/^MW[A-F0-9]{24}$/D', $normalized)) {
    json_response(['ok' => false, 'message' => 'الكود غير صالح أو انتهت صلاحيته.'], 422);
}
$codeHash = hash('sha256', $normalized);
$pdo = null;

try {
    $pdo = db();
    $pdo->beginTransaction();

    // Serializes simultaneous redemptions by the same student. The code lock
    // below also prevents two different students from redeeming one code.
    $lockStudent = $pdo->prepare('SELECT id FROM users WHERE id = ? FOR UPDATE');
    $lockStudent->execute([$userId]);
    if (!$lockStudent->fetchColumn()) {
        $pdo->rollBack();
        json_response(['ok' => false, 'message' => 'لم يتم العثور على حساب الطالب.'], 401);
    }

    $lookup = $pdo->prepare('SELECT id, months, status, redeemed_by, valid_until,
                                  (valid_until IS NOT NULL AND valid_until < NOW()) AS expired
                             FROM subscription_access_codes
                            WHERE code_hash = ? LIMIT 1 FOR UPDATE');
    $lookup->execute([$codeHash]);
    $entry = $lookup->fetch(PDO::FETCH_ASSOC) ?: null;
    if (!$entry || (int) $entry['expired'] === 1 || (string) $entry['status'] === 'revoked') {
        $pdo->rollBack();
        json_response(['ok' => false, 'message' => 'الكود غير صالح أو انتهت صلاحيته.'], 422);
    }

    $subscription = $pdo->prepare('SELECT status, starts_at, expires_at,
                                         (status = \'active\' AND (expires_at IS NULL OR expires_at > NOW())) AS active
                                    FROM subscriptions WHERE user_id = ? LIMIT 1 FOR UPDATE');
    $subscription->execute([$userId]);
    $current = $subscription->fetch(PDO::FETCH_ASSOC) ?: null;

    if ((string) $entry['status'] === 'redeemed') {
        $sameStudent = (int) ($entry['redeemed_by'] ?? 0) === $userId;
        $stillActive = $current && (int) $current['active'] === 1;
        if (!$sameStudent || !$stillActive) {
            $pdo->rollBack();
            json_response(['ok' => false, 'message' => 'هذا الكود مستخدم ولا يمكن تفعيله مرة ثانية.'], 409);
        }
        // Idempotent retry, e.g. if the network dropped after the first success.
        $daysStmt = $pdo->prepare('SELECT CEIL(GREATEST(TIMESTAMPDIFF(SECOND, NOW(), expires_at), 0) / 86400)
                                    FROM subscriptions WHERE user_id = ?');
        $daysStmt->execute([$userId]);
        $days = $daysStmt->fetchColumn();
        $pdo->commit();
        json_response([
            'ok' => true, 'subscribed' => true, 'already_redeemed' => true,
            'duration_months' => (int) $entry['months'],
            'subscription_days_remaining' => $days === null ? null : (int) $days,
            'message' => 'هذا الكود مفعّل على حسابك سابقاً. لم تُضف مدة جديدة.'
        ]);
    }

    if ((string) $entry['status'] !== 'available') {
        $pdo->rollBack();
        json_response(['ok' => false, 'message' => 'الكود غير متاح للتفعيل.'], 409);
    }

    $months = (int) $entry['months'];
    if ($months < 1 || $months > 36) {
        throw new RuntimeException('Invalid subscription-code duration');
    }

    // Do not replace an unlimited active subscription with a finite one.
    if ($current && (int) $current['active'] === 1 && $current['expires_at'] === null) {
        $pdo->rollBack();
        json_response(['ok' => false, 'message' => 'لديك اشتراك مفتوح المدة. لم يُستهلك الكود.'], 409);
    }

    if ($current) {
        // An active finite subscription is extended from its existing expiry.
        // Expired/cancelled subscriptions start a fresh period from NOW().
        $renew = $pdo->prepare("UPDATE subscriptions
            SET starts_at = CASE WHEN status = 'active' AND expires_at > NOW()
                                     THEN COALESCE(starts_at, NOW()) ELSE NOW() END,
                expires_at = DATE_ADD(
                    CASE WHEN status = 'active' AND expires_at > NOW() THEN expires_at ELSE NOW() END,
                    INTERVAL {$months} MONTH),
                status = 'active'
            WHERE user_id = ?");
        $renew->execute([$userId]);
    } else {
        $grant = $pdo->prepare("INSERT INTO subscriptions (user_id, amount, status, starts_at, expires_at)
                                VALUES (?, 0, 'active', NOW(), DATE_ADD(NOW(), INTERVAL {$months} MONTH))");
        $grant->execute([$userId]);
    }

    $consume = $pdo->prepare("UPDATE subscription_access_codes
                                 SET status = 'redeemed', redeemed_by = ?, redeemed_at = NOW()
                               WHERE id = ? AND status = 'available'");
    $consume->execute([$userId, (int) $entry['id']]);
    if ($consume->rowCount() !== 1) {
        throw new RuntimeException('Code was concurrently consumed');
    }

    $daysStmt = $pdo->prepare('SELECT CEIL(GREATEST(TIMESTAMPDIFF(SECOND, NOW(), expires_at), 0) / 86400)
                                FROM subscriptions WHERE user_id = ?');
    $daysStmt->execute([$userId]);
    $days = (int) $daysStmt->fetchColumn();
    $pdo->commit();

    json_response([
        'ok' => true,
        'subscribed' => true,
        'already_redeemed' => false,
        'duration_months' => $months,
        'subscription_days_remaining' => $days,
        'message' => 'تم تفعيل الاشتراك الحقيقي لمدة ' . $months . ' شهر، ويمكنك استخدام المزايا الآن.'
    ]);
} catch (Throwable $error) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('subscription access-code redemption failed: ' . $error->getMessage());
    json_response(['ok' => false, 'message' => 'تعذّر تفعيل الكود من الخادم حالياً. حاول لاحقاً.'], 503);
}
