<?php
declare(strict_types=1);
// Student-facing API requests must not run DDL/migrations during a login request.
// The config/config.php maintenance guard in CONFIG_SCHEMA_GUARD_AR.txt must be
// installed for this setting to take effect (the old config ignores it).
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
$bootstrapStartedAt = microtime(true);
$bootstrapStage = 'includes';
register_shutdown_function(static function () use (&$bootstrapStage, $bootstrapStartedAt): void {
    $elapsed = (int)round((microtime(true) - $bootstrapStartedAt) * 1000);
    if ($elapsed > 2500) error_log('murshid mobile/bootstrap slow stage='.$bootstrapStage.' elapsed_ms='.$elapsed);
    $last = error_get_last();
    if (is_array($last) && in_array((int)($last['type'] ?? 0), [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR], true)) {
        error_log('murshid mobile/bootstrap fatal stage='.$bootstrapStage.' '.($last['message'] ?? 'unknown'));
    }
});
header('Cache-Control: private, no-store, max-age=0');
header('X-Content-Type-Options: nosniff');
try {
require_once __DIR__.'/common.php';
require_once __DIR__.'/../includes/payments.php';
require_once __DIR__.'/../includes/school_ads.php';
$bootstrapStage = 'authentication';
$u=mobile_student();
$csrf=csrf_token();
// Release the PHP session lock before running multiple independent queries;
// other requests from the same logged-in student no longer queue behind this.
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();
$bootstrapStage = 'database';
$pdo=db();
$q=$pdo->prepare('SELECT name FROM grades WHERE id=? LIMIT 1');$q->execute([(int)$u['grade_id']]);
$grade=(string)($q->fetchColumn()?:'');
$avatarProfile=profile((int)$u['id']);
$avatar=avatar_url($avatarProfile['avatar_path']??null) ?: student_avatar_url($avatarProfile['avatar_code']??null,$u['gender']??null);
$q=$pdo->prepare("SELECT id,name FROM subjects WHERE grade_id=? AND status=1 AND name NOT LIKE '%فرنسي%' AND LOWER(name) NOT LIKE '%french%' AND LOWER(name) NOT LIKE '%franc%' AND LOWER(name) NOT LIKE '%fran%' ORDER BY sort_order,id");$q->execute([(int)$u['grade_id']]);
$subjects=$q->fetchAll(PDO::FETCH_ASSOC);
$used=free_answers_used((int)$u['id']);$subscribed=is_subscribed((int)$u['id']);
$daysLeft=null; $offlineExpiryUnix=null;
if($subscribed){try{$exp=db()->prepare("SELECT expires_at FROM subscriptions WHERE user_id=? AND status='active' AND expires_at>NOW() ORDER BY expires_at DESC LIMIT 1");$exp->execute([(int)$u['id']]);$at=$exp->fetchColumn();if($at){$offlineExpiryUnix=strtotime((string)$at);$seconds=$offlineExpiryUnix-time();$daysLeft=max(1,(int)ceil(max(0,$seconds)/86400));}}catch(Throwable $e){error_log('subscription days: '.$e->getMessage());}}

$q=$pdo->prepare('SELECT xp,level,streak,total_answers,correct_answers,exams_completed FROM user_stats WHERE user_id=?');$q->execute([(int)$u['id']]);
$stats=$q->fetch(PDO::FETCH_ASSOC)?:['xp'=>0,'level'=>1,'streak'=>0,'total_answers'=>0,'correct_answers'=>0,'exams_completed'=>0];
$plans=[];foreach(subscription_plans() as $key=>$plan)$plans[]=['id'=>$key,'key'=>$key,'label'=>$plan['label'],'amount'=>(int)$plan['amount']];
// A missing optional ads column must not initiate ALTER TABLE during login.
// Ads fallback is read-only and does not block account access.
$bootstrapStage = 'advertisements';
$ads=[];
try {
    $now=date('Y-m-d H:i:s');
    $a=$pdo->prepare("SELECT id,school_name,ad_text FROM school_ads
        WHERE is_enabled=1
          AND COALESCE(start_at,CONCAT(start_date,' 00:00:00'))<=?
          AND COALESCE(end_at,CONCAT(end_date,' 23:59:59'))>=?
          AND (COALESCE(max_impressions,0)=0 OR COALESCE(impressions,0)<max_impressions)
        ORDER BY id ASC LIMIT 30");
    $a->execute([$now,$now]);
    foreach ($a->fetchAll(PDO::FETCH_ASSOC) as $ad) {
        $ads[]=['id'=>(int)$ad['id'],'school'=>(string)$ad['school_name'],'text'=>(string)$ad['ad_text']];
    }
} catch (Throwable $adError) {
    error_log('murshid mobile/bootstrap ads read failed: '.$adError->getMessage());
}
$bootstrapStage = 'response';
json_response(['ok'=>true,'csrf'=>$csrf,'user'=>['id'=>(int)$u['id'],'name'=>$u['full_name'],'grade'=>$grade,'grade_id'=>(int)$u['grade_id'],'avatar_url'=>$avatar],
    'subjects'=>$subjects,'ads'=>$ads,'stats'=>$stats,'free_limit'=>FREE_QUESTION_LIMIT,'free_used'=>$used,'free_remaining'=>$subscribed?null:max(0,FREE_QUESTION_LIMIT-$used),'subscribed'=>$subscribed,'subscription_days_remaining'=>$daysLeft,'subscription_expires_at_unix'=>$offlineExpiryUnix,'plans'=>$plans]);

} catch (Throwable $e) {
    error_log('murshid mobile/bootstrap failed stage='.$bootstrapStage.': '.$e->getMessage());
    json_response(['ok'=>false,'message'=>'حدث تأخير في تجهيز الحساب من الخادم. حاول مجددًا.'],503);
}
