<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
$user=mobile_student();
header('Content-Type: application/json; charset=utf-8');
if($_SERVER['REQUEST_METHOD']!=='POST')json_response(['ok'=>false,'message'=>'Method not allowed'],405);
$raw=file_get_contents('php://input',false,null,0,8193);
if($raw===false || strlen($raw)>8192)json_response(['ok'=>false],413);
$input=json_decode($raw,true);
if(!is_array($input) || !verify_csrf($input['csrf']??null))json_response(['ok'=>false,'message'=>'طلب غير صالح'],419);
if(!rate_limit('ios-push:'.(int)$user['id'],30,300))json_response(['ok'=>false],429);
$action=(string)($input['action']??'register');
$token=strtolower(trim((string)($input['token']??'')));
if(!preg_match('/^[a-f0-9]{64}$/D',$token))json_response(['ok'=>false,'message'=>'رمز الجهاز غير صالح'],422);
try {
    if($action==='register') {
        $environment=(string)($input['environment']??'production');
        if(!in_array($environment,['production','sandbox'],true))json_response(['ok'=>false,'message'=>'بيئة APNs غير صالحة'],422);
        // A token belongs to the current authenticated account only.
        db()->prepare('INSERT INTO murshid_ios_push_tokens(user_id,token,environment) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),environment=VALUES(environment),updated_at=NOW()')
            ->execute([(int)$user['id'],$token,$environment]);
    } elseif($action==='unregister') {
        db()->prepare('DELETE FROM murshid_ios_push_tokens WHERE user_id=? AND token=?')
            ->execute([(int)$user['id'],$token]);
    } else json_response(['ok'=>false,'message'=>'إجراء غير صالح'],422);
    json_response(['ok'=>true]);
} catch(Throwable $e) {
    error_log('murshid ios push registration: '.$e->getMessage());
    json_response(['ok'=>false,'message'=>'خدمة إشعارات التطبيق لم تُفعّل بعد.'],503);
}
