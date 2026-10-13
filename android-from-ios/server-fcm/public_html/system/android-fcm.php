<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(403); exit; }
define('MURSHID_SKIP_SCHEMA_MAINTENANCE',true);
require_once __DIR__.'/../config/config.php';
require_once __DIR__.'/../includes/android_fcm.php';
if (session_status()===PHP_SESSION_ACTIVE) session_write_close();
$mode=$argv[1]??'--dispatch';
if ($mode==='--install') {
    android_fcm_schema(db());
    echo "Android push schema installed. Existing notification/payment data is unchanged.\n";
    exit;
}
if ($mode==='--install-hook') {
    $path=__DIR__.'/../includes/functions.php'; $source=(string)file_get_contents($path);
    if (str_contains($source,'android_fcm_schedule($userId)')) { echo "Hook already installed.\n"; exit; }
    $anchor='        $notificationId=(int)$pdo->lastInsertId();';
    if (substr_count($source,$anchor)!==1) throw new RuntimeException('Notification hook changed; no file modified.');
    $addition="\n        try { require_once __DIR__.'/android_fcm.php'; android_fcm_schedule(\$userId); }\n        catch(Throwable \$e) { error_log('Android push enqueue deferred'); }";
    $backup=android_fcm_private_dir().'/functions-before-android-push-'.gmdate('YmdHis').'.php';
    if (!is_dir(android_fcm_private_dir()) || !copy($path,$backup)) throw new RuntimeException('Private backup failed; no file modified.');
    chmod($backup,0600);
    $temp=$path.'.android-fcm.tmp';
    if (file_put_contents($temp,str_replace($anchor,$anchor.$addition,$source),LOCK_EX)===false) throw new RuntimeException('Write failed');
    chmod($temp,fileperms($path)&0777);
    if (!rename($temp,$path)) throw new RuntimeException('Atomic install failed');
    echo "Notification hook installed; private backup saved.\n"; exit;
}
if (!android_fcm_ready()) { fwrite(STDERR,"Firebase configuration is missing or does not match the Android package/project.\n"); exit(2); }
if ($mode==='--check') {
    db()->query('SELECT installation_id FROM android_fcm_devices LIMIT 0');
    android_fcm_access_token();
    echo "Android configuration, database and Google authorization are ready. No notification was sent.\n";
    exit;
}
if ($mode!=='--dispatch') { fwrite(STDERR,"Unknown command\n"); exit(2); }
echo json_encode(android_fcm_dispatch(db()),JSON_THROW_ON_ERROR)."\n";
