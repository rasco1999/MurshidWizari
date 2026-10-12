<?php
declare(strict_types=1);

// Install at public_html/mobile/free-month-offer.php.
require_once __DIR__ . '/common.php';
require_once __DIR__ . '/../includes/free_month_campaign.php';
header('Cache-Control: no-store, private');
header('X-Content-Type-Options: nosniff');

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if (!in_array($method, ['GET', 'POST'], true)) {
    json_response(['ok'=>false,'message'=>'طريقة الطلب غير مدعومة.'], 405);
}
$student = mobile_student();
$userId = (int) $student['id'];
$pdo = db();

if ($method === 'GET') {
    try {
        json_response(['ok'=>true,'campaign'=>free_month_status($pdo, free_month_campaign($pdo), $userId)]);
    } catch (Throwable $e) {
        error_log('free-month status: ' . $e->getMessage());
        json_response(['ok'=>false,'message'=>'عرض الشهر المجاني غير متاح حاليًا.'], 503);
    }
}

$raw = (string) file_get_contents('php://input');
if (strlen($raw) > 2048) json_response(['ok'=>false,'message'=>'الطلب غير صالح.'], 413);
$body = json_decode($raw, true);
if (!is_array($body) || !is_string($body['csrf'] ?? null) || !verify_csrf($body['csrf'])) {
    json_response(['ok'=>false,'message'=>'انتهت صلاحية الجلسة. حدّث الصفحة وحاول مجددًا.'], 419);
}
if (!rate_limit('first100_free_month', 5, 3600)) {
    json_response(['ok'=>false,'message'=>'محاولات كثيرة. حاول لاحقًا.'], 429);
}

try {
    $pdo->beginTransaction();
    // Row-level lock serializes simultaneous claims, even across app instances.
    $campaign = free_month_campaign($pdo, true);
    $status = free_month_status($pdo, $campaign, $userId);

    if ($status['already_claimed']) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'سبق أن استفدت من عرض الشهر المجاني.'], 409);
    }
    if (!$status['enabled'] || !$status['started']) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'عرض الشهر المجاني لم يبدأ بعد أو أوقفته الإدارة.'], 409);
    }
    if ($status['expired']) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'انتهت المهلة المحددة لحملة المقاعد المجانية.'], 409);
    }
    if (!$status['eligible_new_student']) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'العرض مخصص للحسابات الجديدة المؤكدة التي أُنشئت خلال فترة الحملة فقط.'], 403);
    }
    if ($status['remaining'] <= 0) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'اكتملت المقاعد المئة المجانية.'], 409);
    }

    $u = $pdo->prepare("SELECT id,role,status,email,email_verified_at,phone,phone_verified_at
                            FROM users WHERE id=? FOR UPDATE");
    $u->execute([$userId]);
    $studentRow = $u->fetch(PDO::FETCH_ASSOC) ?: null;
    if (!$studentRow || $studentRow['role'] !== 'student' || $studentRow['status'] !== 'active') {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'حساب الطالب غير مؤهل للعرض.'], 403);
    }
    if ((!empty($studentRow['email']) && empty($studentRow['email_verified_at'])) ||
        (!empty($studentRow['phone']) && empty($studentRow['phone_verified_at']))) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'أكمل تأكيد بريدك أو رقمك أولاً.'], 403);
    }

    $existing = $pdo->prepare("SELECT status,expires_at FROM subscriptions WHERE user_id=? LIMIT 1 FOR UPDATE");
    $existing->execute([$userId]);
    $currentSub = $existing->fetch(PDO::FETCH_ASSOC) ?: null;
    if ($currentSub && $currentSub['status'] === 'active' &&
        (empty($currentSub['expires_at']) || strtotime((string)$currentSub['expires_at']) > time())) {
        $pdo->rollBack();
        json_response(['ok'=>false,'message'=>'اشتراكك فعّال حاليًا؛ العرض مخصص لغير المشتركين.'], 409);
    }

    $seat = (int) $campaign['claimed_count'] + 1;
    $expireAt = $pdo->query('SELECT DATE_ADD(NOW(), INTERVAL 1 MONTH)')->fetchColumn();
    if (!is_string($expireAt) || $expireAt === '') throw new RuntimeException('Free-month expiration missing');
    $insert = $pdo->prepare('INSERT INTO murshid_free_month_claims
        (campaign_id,user_id,seat_number,claimed_at_utc,subscription_expires_at)
        VALUES (1,?,?,UTC_TIMESTAMP(),?)');
    $insert->execute([$userId,$seat,$expireAt]);

    $subscription = $pdo->prepare("INSERT INTO subscriptions(user_id,amount,status,starts_at,expires_at)
        VALUES (?,0,'active',NOW(),DATE_ADD(NOW(), INTERVAL 1 MONTH))
        ON DUPLICATE KEY UPDATE amount=0,status='active',starts_at=NOW(),expires_at=DATE_ADD(NOW(), INTERVAL 1 MONTH)");
    $subscription->execute([$userId]);

    $update = $pdo->prepare('UPDATE murshid_free_month_campaigns
        SET claimed_count=claimed_count+1,updated_at_utc=UTC_TIMESTAMP()
        WHERE id=1 AND claimed_count < capacity');
    $update->execute();
    if ($update->rowCount() !== 1) throw new RuntimeException('Free-month seat already full');

    $pdo->commit();
    try {
        if (function_exists('audit_log')) audit_log('حجز شهر مجاني من حملة أول 100 طالب','subscription',(string)$userId);
    } catch (Throwable $e) { /* Campaign has already committed. */ }
    json_response([
        'ok'=>true,
        'message'=>'مبروك! حصلت على اشتراك مجاني كامل لمدة شهر من الآن.',
        'seat_number'=>$seat,
        'campaign'=>free_month_status($pdo, free_month_campaign($pdo), $userId),
    ]);
} catch (PDOException $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ((string)$e->getCode() === '23000') {
        json_response(['ok'=>false,'message'=>'سبق حجز هذا المقعد أو استفدت من العرض. حدّث الصفحة.'],409);
    }
    error_log('free-month claim PDO error: '.$e->getMessage());
    json_response(['ok'=>false,'message'=>'تعذر حجز الشهر المجاني الآن. حاول مجددًا.'],503);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('free-month claim: '.$e->getMessage());
    json_response(['ok'=>false,'message'=>'تعذر تفعيل العرض الآن. حاول مجددًا.'],503);
}
