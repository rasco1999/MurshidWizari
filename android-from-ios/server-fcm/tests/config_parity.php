<?php
declare(strict_types=1);
// Synthetic configuration only, no external network calls, no live credentials.
$root=sys_get_temp_dir().'/murshid-fcm-config-'.bin2hex(random_bytes(5));
if(!mkdir($root,0700)) throw new RuntimeException('Cannot create temporary test directory');
$client=['project_info'=>['project_id'=>'murshid-qa-demo','project_number'=>'123456789'],
 'client'=>[]];
foreach(['com.murshidwizari.student','com.muriq.murshid.iosparity'] as $i=>$pkg) {
 $client['client'][]=['client_info'=>['android_client_info'=>['package_name'=>$pkg],
  'mobilesdk_app_id'=>'1:123456789:android:'.str_repeat((string)($i+1),20)],
  'api_key'=>[['current_key'=>'AIza'.str_repeat('A',35)]]];
}
$service=['type'=>'service_account','project_id'=>'murshid-qa-demo',
 'private_key'=>'TEST-ONLY-PLACEHOLDER-NOT-A-KEY','client_email'=>'qa@example.invalid'];
file_put_contents($root.'/google-services.json',json_encode($client));
file_put_contents($root.'/firebase-service-account.json',json_encode($service));
putenv('MURSHID_FCM_PRIVATE_DIR='.$root);
require __DIR__.'/../public_html/includes/android_fcm.php';
try {
 $old=android_fcm_settings();
 $new=android_fcm_settings('com.muriq.murshid.iosparity');
 if (($old['firebase']['package_name']??'')!=='com.murshidwizari.student') throw new RuntimeException('Legacy app selection failed');
 if (($new['firebase']['package_name']??'')!=='com.muriq.murshid.iosparity') throw new RuntimeException('Compose app selection failed');
 if (($new['firebase']['project_id']??'')!==($old['firebase']['project_id']??'')) throw new RuntimeException('Firebase project mismatch');
 if (android_fcm_settings('other.bad.app')!==[]) throw new RuntimeException('Unknown package accepted');
 if (isset($new['firebase']['private_key'])) throw new RuntimeException('Sensitive key exposed in public Firebase config');
 echo "PASS: old and Compose package selection, allowlist and secret isolation.\n";
} finally {
 unlink($root.'/google-services.json'); unlink($root.'/firebase-service-account.json');rmdir($root);
}
