<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
mobile_student();
if ($_SERVER['REQUEST_METHOD']!=='GET') json_response(['ok'=>false],405);
require_once __DIR__.'/../includes/android_fcm.php';
if (!android_fcm_ready()) json_response(['ok'=>true,'enabled'=>false]);
// Explicit allowlist: service-account fields can never be returned to the client.
json_response(['ok'=>true,'enabled'=>true,'firebase'=>android_fcm_settings()['firebase']]);
