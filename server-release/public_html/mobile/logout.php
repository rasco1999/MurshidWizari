<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
$student=mobile_student();
if($_SERVER['REQUEST_METHOD']!=='POST')json_response(['ok'=>false],405);
$body=json_decode((string)file_get_contents('php://input'),true);
if(!verify_csrf(is_array($body)?($body['csrf']??null):null))json_response(['ok'=>false,'message'=>'انتهت صلاحية الطلب.'],419);
$uid=(int)$student['id'];
if(is_array($body)) {
    try {
        require_once __DIR__.'/../includes/android_fcm.php';
        $installation=(string)($body['android_push_installation_id']??'');
        $binding=(string)($body['android_push_binding_id']??'');
        if(android_fcm_valid_uuid($installation) && android_fcm_valid_uuid($binding)) {
            android_fcm_revoke(db(),$uid,$installation,$binding);
        }
    } catch(Throwable $e){error_log('Android logout revocation deferred');}
    $token=strtolower((string)($body['ios_push_token']??''));
    if(preg_match('/^[0-9a-f]{64}$/D',$token)) {
        try {db()->prepare('DELETE FROM murshid_ios_push_tokens WHERE user_id=? AND token=?')->execute([$uid,$token]);}
        catch(Throwable $e){error_log('iOS logout revocation deferred');}
    }
}
require_once __DIR__.'/../includes/single_session.php';
single_session_release_current();
$_SESSION=[];session_destroy();json_response(['ok'=>true]);
