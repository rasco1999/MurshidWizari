<?php
declare(strict_types=1);
/** iOS school-ad notification queue. Publish is write-only; delivery runs in CLI cron. */

function murshid_apns_cfg(): array {
    $file = __DIR__ . '/../config/native_push.php';
    return is_file($file) ? (array) require $file : [];
}
function murshid_apns_ready(): bool {
    $c=murshid_apns_cfg();
    return function_exists('curl_init') && extension_loaded('openssl')
        && preg_match('/^[A-Z0-9]{10}$/D',(string)($c['team_id']??''))===1
        && preg_match('/^[A-Z0-9]{10}$/D',(string)($c['key_id']??''))===1
        && str_contains((string)($c['private_key']??''),'BEGIN PRIVATE KEY')
        && (string)($c['topic']??'')==='com.muriq.murshid';
}
function murshid_apns_base64(string $s): string {return rtrim(strtr(base64_encode($s),'+/','-_'),'=');}
function murshid_apns_signature(string $der): string {
    // Convert the ASN.1 DER ECDSA signature to the fixed 64-byte JOSE form.
    $i=0;$size=strlen($der);
    $len=function() use (&$i,$size,$der): int {
        if($i >= $size)throw new RuntimeException('Invalid ECDSA length');
        $x=ord($der[$i++]);if($x<128)return $x;
        $count=$x&127;if($count<1||$count>4||$i+$count>$size)throw new RuntimeException('Invalid DER length');
        $n=0;while($count-- > 0)$n=($n<<8)|ord($der[$i++]);return $n;
    };
    if($size<8||ord($der[$i++])!==0x30)throw new RuntimeException('Invalid DER sequence');
    $seq=$len();if($seq!==$size-$i)throw new RuntimeException('Invalid DER sequence size');
    $parts=[];
    foreach([0,1] as $_) {
        if($i >= $size||ord($der[$i++])!==0x02)throw new RuntimeException('Invalid DER integer');
        $n=$len();if($n<1||$n>33||$i+$n>$size)throw new RuntimeException('Invalid ECDSA integer size');
        $val=ltrim(substr($der,$i,$n),"\0");$i+=$n;
        if(strlen($val)>32)throw new RuntimeException('Invalid ECDSA coordinate');
        $parts[]=str_pad($val,32,"\0",STR_PAD_LEFT);
    }
    if($i!==$size)throw new RuntimeException('Invalid ECDSA trailing bytes');
    return implode('',$parts);
}
function murshid_apns_jwt(): string {
    $c=murshid_apns_cfg();
    $h=murshid_apns_base64(json_encode(['alg'=>'ES256','kid'=>$c['key_id']],JSON_THROW_ON_ERROR));
    $p=murshid_apns_base64(json_encode(['iss'=>$c['team_id'],'iat'=>time()],JSON_THROW_ON_ERROR));
    $data=$h.'.'.$p;$sig='';
    if(!openssl_sign($data,$sig,(string)$c['private_key'],OPENSSL_ALGO_SHA256))throw new RuntimeException('APNs key invalid');
    return $data.'.'.murshid_apns_base64(murshid_apns_signature($sig));
}
function murshid_ad_push_when(array $ad): ?string {
    if((int)($ad['is_enabled']??0)!==1)return null;
    $start=trim((string)($ad['start_at']??''));
    $end=trim((string)($ad['end_at']??''));
    if($start==='')$start=(string)($ad['start_date']??'').' 00:00:00';
    if($end==='')$end=(string)($ad['end_date']??'').' 23:59:59';
    if(!preg_match('/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/D',$start) ||
       !preg_match('/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/D',$end))return null;
    $now=(new DateTimeImmutable('now',new DateTimeZone('Asia/Baghdad')))->format('Y-m-d H:i:s');
    if($end<=$now)return null;
    return $start>$now?$start:$now;
}
function murshid_school_push_enqueue(PDO $pdo,int $adID): bool {
    if($adID<=0)return false;
    // Called only after an administrator explicitly creates/enables an ad.
    try {
        $q=$pdo->prepare('SELECT id,school_name,ad_text,start_at,end_at,start_date,end_date,is_enabled FROM school_ads WHERE id=? LIMIT 1');
        $q->execute([$adID]);$ad=$q->fetch(PDO::FETCH_ASSOC);
        if(!is_array($ad)||!($when=murshid_ad_push_when($ad)))return false;
        // When an existing future publication was briefly disabled/re-enabled,
        // reschedule that same event rather than notifying each student twice.
        $existing=$pdo->prepare("SELECT id FROM murshid_school_push_events WHERE ad_id=? AND status IN ('pending','working') ORDER BY id LIMIT 1");
        $existing->execute([$adID]);$eventID=(int)$existing->fetchColumn();
        if($eventID>0){
            $pdo->prepare("UPDATE murshid_school_push_events SET available_at=? WHERE id=? AND status='pending'")
                ->execute([$when,$eventID]);
            return true;
        }
        $ins=$pdo->prepare("INSERT INTO murshid_school_push_events(ad_id,available_at,status) VALUES(?,?,'pending')");
        $ins->execute([$adID,$when]);return true;
    } catch(Throwable $e) {
        // Advertising must not fail if the optional APNs tables have not been installed.
        error_log('school APNs enqueue: '.$e->getMessage());return false;
    }
}
function murshid_apns_send(string $token,string $environment,string $jwt,array $payload): array {
    $url='https://'.($environment==='sandbox'?'api.sandbox.push.apple.com':'api.push.apple.com').'/3/device/'.$token;
    $ch=curl_init($url);if($ch===false)return ['status'=>0,'reason'=>'curl-init'];
    $body=json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>$body,CURLOPT_RETURNTRANSFER=>true,
        CURLOPT_CONNECTTIMEOUT=>5,CURLOPT_TIMEOUT=>12,CURLOPT_HTTP_VERSION=>CURL_HTTP_VERSION_2TLS,
        CURLOPT_HTTPHEADER=>['authorization: bearer '.$jwt,'apns-topic: com.muriq.murshid','apns-push-type: alert',
            'apns-priority: 10','apns-expiration: '.(string)(time()+86400),'content-type: application/json']]);
    $response=curl_exec($ch);$status=(int)curl_getinfo($ch,CURLINFO_RESPONSE_CODE);
    $bodyJSON=json_decode(is_string($response)?$response:'',true);
    $reason=is_array($bodyJSON)?(string)($bodyJSON['reason']??''):(string)curl_error($ch);
    curl_close($ch);return ['status'=>$status,'reason'=>$reason];
}
function murshid_school_push_worker(PDO $pdo,int $limit=25): array {
    if(!murshid_apns_ready())return ['ok'=>false,'reason'=>'APNs credentials missing'];
    $limit=max(1,min(50,$limit));
    // Lease prevents two overlapping cron runs sending the same batch.
    $pdo->beginTransaction();
    try {
        $q=$pdo->query("SELECT id,ad_id,cursor_token_id FROM murshid_school_push_events
            WHERE available_at<=NOW() AND (status='pending' OR (status='working' AND locked_until<NOW()))
            ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
        $event=$q->fetch(PDO::FETCH_ASSOC);
        if(!$event){$pdo->commit();return ['ok'=>true,'processed'=>0];}
        $pdo->prepare("UPDATE murshid_school_push_events SET status='working',locked_until=DATE_ADD(NOW(),INTERVAL 8 MINUTE) WHERE id=?")->execute([(int)$event['id']]);
        $pdo->commit();
    } catch(Throwable $e) {
        if($pdo->inTransaction())$pdo->rollBack();throw $e;
    }
    $eventID=(int)$event['id'];$adID=(int)$event['ad_id'];$cursor=(int)$event['cursor_token_id'];
    try {
        $q=$pdo->prepare('SELECT id,school_name,ad_text,start_at,end_at,start_date,end_date,is_enabled,max_impressions,impressions FROM school_ads WHERE id=? LIMIT 1');
        $q->execute([$adID]);$ad=$q->fetch(PDO::FETCH_ASSOC);
        $effectiveWhen=is_array($ad)?murshid_ad_push_when($ad):null;
        if(!is_array($ad)||!$effectiveWhen||((int)($ad['max_impressions']??0)>0 && (int)$ad['impressions'] >= (int)$ad['max_impressions'])){
            $pdo->prepare("UPDATE murshid_school_push_events SET status='done',locked_until=NULL WHERE id=?")->execute([$eventID]);
            return ['ok'=>true,'processed'=>0,'inactive'=>true];
        }
        $now=(new DateTimeImmutable('now',new DateTimeZone('Asia/Baghdad')))->format('Y-m-d H:i:s');
        if($effectiveWhen>$now){
            $pdo->prepare("UPDATE murshid_school_push_events SET status='pending',available_at=?,locked_until=NULL WHERE id=?")
                ->execute([$effectiveWhen,$eventID]);
            return ['ok'=>true,'processed'=>0,'rescheduled'=>true];
        }
        $q=$pdo->prepare("SELECT d.id,d.user_id,d.token,d.environment FROM murshid_ios_push_tokens d
            JOIN users u ON u.id=d.user_id WHERE d.id>? AND u.role='student' AND u.status='active' ORDER BY d.id LIMIT ".$limit);
        $q->execute([$cursor]);$tokens=$q->fetchAll(PDO::FETCH_ASSOC)?:[];
        $jwt=murshid_apns_jwt();
        $payload=['aps'=>['alert'=>['title'=>'إعلان مدرسة: '.mb_substr((string)$ad['school_name'],0,70),
            'body'=>mb_substr((string)$ad['ad_text'],0,180)],'category'=>'MURSHID_SCHOOL_AD','thread-id'=>'school-ads'],
            'type'=>'school_ad','ad_id'=>$adID,'event_id'=>$eventID];
        $sent=0;$temporary=false;
        foreach($tokens as $row){
            $res=murshid_apns_send((string)$row['token'],(string)$row['environment'],$jwt,$payload);
            $status=$res['status'];$reason=$res['reason'];
            if($status===200){
                $sent++;$cursor=(int)$row['id'];
                $pdo->prepare('UPDATE murshid_school_push_events SET cursor_token_id=? WHERE id=?')->execute([$cursor,$eventID]);
                continue;
            }
            if($status===410 || ($status===400 && in_array($reason,['BadDeviceToken','DeviceTokenNotForTopic','Unregistered'],true))){
                $pdo->prepare('DELETE FROM murshid_ios_push_tokens WHERE id=? AND user_id=? AND token=?')->execute([(int)$row['id'],(int)$row['user_id'],(string)$row['token']]);
                $cursor=(int)$row['id'];continue;
            }
            // Retry transient failures from the same cursor; do not re-send accepted devices.
            if($status===0||$status===429||$status>=500||$status===403){$temporary=true;break;}
            error_log('school APNs rejected token id='.$row['id'].' HTTP '.$status.' '.substr($reason,0,80));
            $cursor=(int)$row['id'];
        }
        $complete=!$temporary && count($tokens)<$limit;
        $sql="UPDATE murshid_school_push_events SET cursor_token_id=?,sent_count=sent_count+?,status=?,locked_until=NULL,available_at=".
            ($temporary?'DATE_ADD(NOW(),INTERVAL 5 MINUTE)':'NOW()')." WHERE id=?";
        $pdo->prepare($sql)->execute([$cursor,$sent,$complete?'done':'pending',$eventID]);
        return ['ok'=>true,'processed'=>count($tokens),'sent'=>$sent,'done'=>$complete,'retry'=>$temporary];
    } catch(Throwable $e) {
        $pdo->prepare("UPDATE murshid_school_push_events SET status='pending',locked_until=NULL,available_at=DATE_ADD(NOW(),INTERVAL 5 MINUTE),attempts=attempts+1 WHERE id=?")->execute([$eventID]);
        error_log('school APNs worker: '.$e->getMessage());return ['ok'=>false,'reason'=>'dispatch error'];
    }
}
