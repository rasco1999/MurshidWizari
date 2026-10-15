<?php
declare(strict_types=1);
// Cron/CLI only: never allow visitors to trigger a bulk notification.
if(PHP_SAPI!=='cli'){http_response_code(403);exit('CLI only');}
@set_time_limit(300);
if(!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE'))define('MURSHID_SKIP_SCHEMA_MAINTENANCE',true);
require_once __DIR__.'/../config/config.php';
require_once __DIR__.'/../includes/school_ad_apns.php';
try { $pdo=db(); $pdo->exec("SET time_zone='+03:00'"); $result=murshid_school_push_worker($pdo,25); }
catch(Throwable $e){error_log('school APNs CLI fatal: '.$e->getMessage());$result=['ok'=>false,'reason'=>'database unavailable'];}
echo json_encode($result,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES).PHP_EOL;
exit(($result['ok']??false)?0:1);
