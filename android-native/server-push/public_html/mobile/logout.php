<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
$user=mobile_student();
if($_SERVER['REQUEST_METHOD']!=='POST')json_response(['ok'=>false],405);
$body=json_decode((string)file_get_contents('php://input'),true);
if(!verify_csrf(is_array($body)?($body['csrf']??null):null))json_response(['ok'=>false,'message'=>'انتهت صلاحية الطلب.'],419);
require_once __DIR__.'/../includes/android_fcm.php';
try {
    android_fcm_revoke(db(),(int)$user['id'],(string)($body['android_push_installation_id']??''),(string)($body['android_push_binding_id']??''));
} catch(Throwable $e) { error_log('Android push logout revocation deferred; local binding is removed'); }
require_once __DIR__.'/../includes/single_session.php';
single_session_release_current();
$_SESSION=[];session_destroy();json_response(['ok'=>true]);
