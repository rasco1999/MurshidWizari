<?php
declare(strict_types=1);

// No keys in public_html or in the APK. Both JSON files belong outside the web root.
function android_fcm_private_dir(): string {
    return rtrim((string)(getenv('MURSHID_FCM_PRIVATE_DIR') ?: dirname(__DIR__, 2).'/murshid-private'), '/');
}
function android_fcm_settings(): array {
    static $settings;
    if (is_array($settings)) return $settings;
    $settings=[];
    try {
        $dir=realpath(android_fcm_private_dir()); $web=realpath(__DIR__.'/..');
        if (!$dir || !$web || $dir===$web || str_starts_with($dir, $web.DIRECTORY_SEPARATOR)) return [];
        if (!is_readable($dir.'/google-services.json') || !is_readable($dir.'/firebase-service-account.json')) return [];
        $client=json_decode((string)file_get_contents($dir.'/google-services.json'), true, 32, JSON_THROW_ON_ERROR);
        $service=json_decode((string)file_get_contents($dir.'/firebase-service-account.json'), true, 32, JSON_THROW_ON_ERROR);
        $project=(string)($client['project_info']['project_id']??'');
        if (!preg_match('/^[a-z0-9][a-z0-9-]{4,62}$/D', $project) || $project!==($service['project_id']??'') ||
            ($service['type']??'')!=='service_account' || empty($service['private_key']) || empty($service['client_email'])) return [];
        foreach (($client['client']??[]) as $app) {
            if (($app['client_info']['android_client_info']['package_name']??'')!=='com.muriq.murshid.iosparity') continue;
            $settings=['firebase'=>[
                'package_name'=>'com.muriq.murshid.iosparity', 'project_id'=>$project,
                'sender_id'=>(string)($client['project_info']['project_number']??''),
                'app_id'=>(string)($app['client_info']['mobilesdk_app_id']??''),
                'api_key'=>(string)($app['api_key'][0]['current_key']??'')], 'service'=>$service];
            break;
        }
    } catch (Throwable $e) { return []; }
    return $settings;
}
function android_fcm_ready(): bool {
    return !empty(android_fcm_settings()) && function_exists('curl_init') && extension_loaded('openssl');
}
function android_fcm_valid_uuid(string $value): bool {
    return (bool)preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D', $value);
}
function android_fcm_validate_registration(array $input): bool {
    $token=(string)($input['token']??'');
    return android_fcm_valid_uuid((string)($input['installation_id']??'')) &&
        android_fcm_valid_uuid((string)($input['binding_id']??'')) &&
        strlen($token)>=20 && strlen($token)<=4096 && (bool)preg_match('/^[A-Za-z0-9_:.-]+$/D', $token);
}
function android_fcm_schema(PDO $pdo): void {
    $pdo->exec("CREATE TABLE IF NOT EXISTS android_fcm_devices (
        installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        binding_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        token TEXT NOT NULL,
        token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        last_notification_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
        attempts INT UNSIGNED NOT NULL DEFAULT 0,
        next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_android_fcm_token(token_hash),
        INDEX idx_android_fcm_user(user_id),
        INDEX idx_android_fcm_due(next_attempt_at),
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
function android_fcm_register(PDO $pdo, int $userId, array $input): void {
    if ($userId<=0 || !android_fcm_validate_registration($input)) throw new InvalidArgumentException('invalid registration');
    $installation=$input['installation_id']; $binding=$input['binding_id']; $token=$input['token'];
    $pdo->beginTransaction();
    try {
        $old=$pdo->prepare('SELECT user_id,binding_id,last_notification_id FROM android_fcm_devices WHERE installation_id=? FOR UPDATE');
        $old->execute([$installation]); $row=$old->fetch(PDO::FETCH_ASSOC);
        // A fresh account binding starts at the current inbox, never replays another user's backlog.
        if ($row && (int)$row['user_id']===$userId && hash_equals($row['binding_id'],$binding)) $cursor=(int)$row['last_notification_id'];
        else { $q=$pdo->prepare('SELECT COALESCE(MAX(id),0) FROM notifications WHERE user_id=?'); $q->execute([$userId]); $cursor=(int)$q->fetchColumn(); }
        $hash=hash('sha256',$token);
        $pdo->prepare('DELETE FROM android_fcm_devices WHERE token_hash=? AND installation_id<>?')->execute([$hash,$installation]);
        $pdo->prepare('INSERT INTO android_fcm_devices(installation_id,user_id,binding_id,token,token_hash,last_notification_id) VALUES(?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
            next_attempt_at=IF(user_id=VALUES(user_id) AND binding_id=VALUES(binding_id) AND token_hash=VALUES(token_hash),next_attempt_at,NOW()),
            attempts=IF(user_id=VALUES(user_id) AND binding_id=VALUES(binding_id) AND token_hash=VALUES(token_hash),attempts,0),
            user_id=VALUES(user_id),binding_id=VALUES(binding_id),token=VALUES(token),token_hash=VALUES(token_hash),
            last_notification_id=VALUES(last_notification_id),updated_at=NOW()')
            ->execute([$installation,$userId,$binding,$token,$hash,$cursor]);
        $pdo->commit();
    } catch (Throwable $e) { if ($pdo->inTransaction()) $pdo->rollBack(); throw $e; }
}
function android_fcm_revoke(PDO $pdo, int $userId, string $installation, string $binding): void {
    if ($userId<=0 || !android_fcm_valid_uuid($installation) || !android_fcm_valid_uuid($binding)) return;
    $pdo->prepare('DELETE FROM android_fcm_devices WHERE installation_id=? AND user_id=? AND binding_id=?')
        ->execute([$installation,$userId,$binding]);
}
function android_fcm_b64(string $text): string { return rtrim(strtr(base64_encode($text),'+/','-_'),'='); }
function android_fcm_access_token(): string {
    static $cached=''; static $expires=0;
    if ($cached!=='' && $expires>time()+60) return $cached;
    $service=android_fcm_settings()['service']??[];
    $head=android_fcm_b64(json_encode(['alg'=>'RS256','typ'=>'JWT'],JSON_THROW_ON_ERROR));
    $claims=android_fcm_b64(json_encode(['iss'=>$service['client_email']??'',
        'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token',
        'iat'=>time(),'exp'=>time()+3600],JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR));
    $unsigned=$head.'.'.$claims; $signature='';
    if (!openssl_sign($unsigned,$signature,(string)($service['private_key']??''),OPENSSL_ALGO_SHA256)) throw new RuntimeException('FCM signing failed');
    $ch=curl_init('https://oauth2.googleapis.com/token');
    curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>http_build_query([
        'grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$unsigned.'.'.android_fcm_b64($signature)]),
        CURLOPT_HTTPHEADER=>['Content-Type: application/x-www-form-urlencoded'],CURLOPT_RETURNTRANSFER=>true,
        CURLOPT_CONNECTTIMEOUT=>5,CURLOPT_TIMEOUT=>10,CURLOPT_FOLLOWLOCATION=>false]);
    $raw=curl_exec($ch); $status=(int)curl_getinfo($ch,CURLINFO_RESPONSE_CODE); curl_close($ch);
    $result=json_decode((string)$raw,true);
    if ($status!==200 || empty($result['access_token'])) throw new RuntimeException('FCM authorization failed');
    $cached=(string)$result['access_token']; $expires=time()+min(3600,(int)($result['expires_in']??300));
    return $cached;
}
function android_fcm_payload(array $device, int $notificationId): array {
    return ['message'=>['token'=>$device['token'],'data'=>[
        'binding_id'=>$device['binding_id'],'notification_id'=>(string)$notificationId, 'type'=>(($device['notice_type']??'')==='school_ad'?'school_ad':'account_notice')],
        'android'=>['priority'=>'HIGH','ttl'=>'86400s','collapse_key'=>'student-inbox']]];
}
function android_fcm_send(array $device, int $notificationId): array {
    $project=android_fcm_settings()['firebase']['project_id'];
    $ch=curl_init('https://fcm.googleapis.com/v1/projects/'.rawurlencode($project).'/messages:send');
    $retry=0;
    curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>json_encode(android_fcm_payload($device,$notificationId),JSON_THROW_ON_ERROR),
        CURLOPT_HTTPHEADER=>['Authorization: Bearer '.android_fcm_access_token(),'Content-Type: application/json'],
        CURLOPT_RETURNTRANSFER=>true,CURLOPT_CONNECTTIMEOUT=>5,CURLOPT_TIMEOUT=>10,CURLOPT_FOLLOWLOCATION=>false,
        CURLOPT_HEADERFUNCTION=>static function($handle,string $header) use (&$retry): int {
            if (stripos($header,'Retry-After:')===0) { $value=trim(substr($header,12));
                $retry=ctype_digit($value)?(int)$value:max(0,(int)strtotime($value)-time()); }
            return strlen($header);
        }]);
    $raw=curl_exec($ch); $status=(int)curl_getinfo($ch,CURLINFO_RESPONSE_CODE); curl_close($ch);
    $body=json_decode((string)$raw,true); $unregistered=false;
    foreach (($body['error']['details']??[]) as $detail) if (($detail['errorCode']??'')==='UNREGISTERED') $unregistered=true;
    // Never persist/log provider response bodies: they may contain a registration token.
    return ['ok'=>$status===200,'unregistered'=>$unregistered,'retry_after'=>max(0,min(86400,$retry)),'status'=>$status];
}
function android_fcm_dispatch(PDO $pdo, int $limit=40, ?callable $sender=null, ?int $onlyUser=null): array {
    $sender=$sender??'android_fcm_send'; $stats=['sent'=>0,'revoked'=>0,'retry'=>0,'scanned'=>0];
    if (!(int)$pdo->query("SELECT GET_LOCK('murshid_android_fcm_dispatch',0)")->fetchColumn()) return $stats;
    $started=microtime(true);
    try {
        $query=$pdo->prepare('SELECT * FROM android_fcm_devices WHERE next_attempt_at<=NOW() AND updated_at>DATE_SUB(NOW(),INTERVAL 60 DAY)'.
            ($onlyUser!==null?' AND user_id=?':'').' ORDER BY next_attempt_at,installation_id LIMIT '.max(1,min(200,$limit)));
        $query->execute($onlyUser!==null?[$onlyUser]:[]);
        foreach ($query->fetchAll(PDO::FETCH_ASSOC) as $device) {
            if (microtime(true)-$started>40) break;
            $stats['scanned']++;
            $q=$pdo->prepare('SELECT COALESCE(MAX(id),0) newest,COALESCE(MAX(CASE WHEN is_read=0 THEN id ELSE NULL END),0) unread FROM notifications WHERE user_id=? AND id>?');
            $q->execute([(int)$device['user_id'],(int)$device['last_notification_id']]); $next=$q->fetch(PDO::FETCH_ASSOC);
            $cursor=max((int)$device['last_notification_id'],(int)$next['newest']);
            $where=[$device['installation_id'],$device['binding_id'],$device['token_hash']];
            $result=['ok'=>true];
            if ((int)$next['unread']>0) {
                $kind=$pdo->prepare('SELECT title FROM notifications WHERE id=? AND user_id=? LIMIT 1');
                $kind->execute([(int)$next['unread'],(int)$device['user_id']]);
                $device['notice_type']=((string)$kind->fetchColumn()==='إعلان مدرسة جديد')?'school_ad':'account_notice';
                try { $result=$sender($device,(int)$next['unread']); }
                catch (Throwable $e) { $result=['ok'=>false]; }
            }
            if (!empty($result['unregistered'])) {
                $pdo->prepare('DELETE FROM android_fcm_devices WHERE installation_id=? AND binding_id=? AND token_hash=?')->execute($where); $stats['revoked']++;
            } elseif (!empty($result['ok'])) {
                $pdo->prepare('UPDATE android_fcm_devices SET last_notification_id=?,attempts=0,next_attempt_at=DATE_ADD(NOW(),INTERVAL 1 MINUTE) WHERE installation_id=? AND binding_id=? AND token_hash=?')
                    ->execute(array_merge([$cursor],$where)); if ((int)$next['unread']>0) $stats['sent']++;
            } else {
                $delay=max((int)($result['retry_after']??0),min(21600,60*(2**min(9,(int)$device['attempts']))));
                $pdo->prepare('UPDATE android_fcm_devices SET attempts=LEAST(attempts+1,1000),next_attempt_at=DATE_ADD(NOW(),INTERVAL ? SECOND) WHERE installation_id=? AND binding_id=? AND token_hash=?')
                    ->execute(array_merge([$delay],$where)); $stats['retry']++;
            }
        }
    } finally { $pdo->query("SELECT RELEASE_LOCK('murshid_android_fcm_dispatch')"); }
    return $stats;
}
function android_fcm_schedule(int $userId): void {
    if ($userId<=0 || !android_fcm_ready() || !function_exists('fastcgi_finish_request')) return;
    static $users=[]; static $scheduled=false; if (count($users)<3) $users[$userId]=true;
    if ($scheduled) return; $scheduled=true;
    register_shutdown_function(static function() use (&$users): void {
        fastcgi_finish_request();
        foreach (array_keys($users) as $id) try {
            // A new notification can wake an idle installation, but never bypass a failed-send backoff.
            db()->prepare('UPDATE android_fcm_devices SET next_attempt_at=NOW() WHERE user_id=? AND attempts=0')->execute([(int)$id]);
            android_fcm_dispatch(db(),3,null,(int)$id);
        } catch (Throwable $e) { error_log('Android push dispatch deferred to cron'); }
    });
}
