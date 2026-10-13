<?php
declare(strict_types=1);
require __DIR__.'/../public_html/includes/android_fcm.php';
$device=['binding_id'=>'7a46e9f0-0c50-45a7-a163-a088702b2047',
         'token'=>'not-a-real-device-token',
         'notice_type'=>'school_ad'];
$result=android_fcm_payload($device,305)['message']['data'];
if (($result['type']??'')!=='school_ad' || ($result['notification_id']??'')!=='305' ||
    ($result['binding_id']??'')!==$device['binding_id']) {
    throw new RuntimeException('School FCM payload lost account binding or tab route');
}
$device['notice_type']='account_notice';
$result=android_fcm_payload($device,306)['message']['data'];
if (($result['type']??'')!=='account_notice') {
    throw new RuntimeException('Personal notifications must not route to school ads');
}
echo "FCM payload routing test passed\n";
