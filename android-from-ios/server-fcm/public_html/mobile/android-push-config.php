<?php
declare(strict_types=1);
require_once __DIR__.'/common.php';
mobile_student();
if ($_SERVER['REQUEST_METHOD'] !== 'GET') json_response(['ok'=>false],405);
require_once __DIR__.'/../includes/android_fcm.php';
// Do not infer a package from arbitrary untrusted headers. Restrict it to
// known native package IDs, and retain the existing no-query legacy contract.
$package = isset($_GET['package']) ? (string)$_GET['package'] : null;
if ($package !== null && !in_array($package,
    ['com.murshidwizari.student', 'com.muriq.murshid.iosparity'], true)) {
    json_response(['ok'=>false,'message'=>'Unsupported Android package'],422);
}
if (!android_fcm_ready($package)) json_response(['ok'=>true,'enabled'=>false]);
json_response(['ok'=>true,'enabled'=>true,'firebase'=>android_fcm_settings($package)['firebase']]);
