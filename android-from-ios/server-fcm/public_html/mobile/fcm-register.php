<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
$user=mobile_student();
if ($_SERVER['REQUEST_METHOD']!=='POST') json_response(['ok'=>false],405);
$raw=file_get_contents('php://input',false,null,0,8193);
if ($raw===false || strlen($raw)>8192) json_response(['ok'=>false],413);
$body=json_decode($raw,true);
if (!is_array($body) || !verify_csrf($body['csrf']??null)) json_response(['ok'=>false,'message'=>'انتهت صلاحية الطلب.'],419);
require_once __DIR__.'/../includes/android_fcm.php';
if (!android_fcm_validate_registration($body)) json_response(['ok'=>false,'message'=>'بيانات الجهاز غير صالحة.'],422);
if (!android_fcm_ready()) json_response(['ok'=>false,'message'=>'الإشعارات الفورية غير متاحة حاليًا.'],503);
if (!rate_limit('android-fcm:'.(int)$user['id'],20,300)) json_response(['ok'=>false],429);
try {
    android_fcm_register(db(),(int)$user['id'],$body);
    json_response(['ok'=>true]);
} catch (Throwable $e) { error_log('Android push registration failed'); json_response(['ok'=>false],503); }
