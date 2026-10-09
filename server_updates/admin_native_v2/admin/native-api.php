<?php
declare(strict_types=1);
// Native iOS administration API. Install beside admin pages:
// public_html/admin/native-api.php; strictly JSON (no HTML/WebView).
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);
require_once __DIR__ . '/../config/config.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, private');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');
header('Access-Control-Allow-Origin: none');
function mn_reply(array $data, int $code=200): never {http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_INVALID_UTF8_SUBSTITUTE);exit;}
function mn_fail(string $message,int $http=422): never {mn_reply(['ok'=>false,'message'=>$message],$http);}
function mn_str(array $x,string $k,int $length=500): string { $v=trim((string)($x[$k]??''));if (mb_strlen($v)>$length) mn_fail('النص أطول من الحد المسموح.');return $v;}
function mn_int(array $x,string $k): int {return max(0,(int)($x[$k]??0));}
function mn_date(string $v): bool {$d=DateTimeImmutable::createFromFormat('!Y-m-d',$v);return $d && $d->format('Y-m-d')===$v;}
function mn_required(string $v,string $field): string {if($v==='')mn_fail('حقل '.$field.' مطلوب.');return $v;}
function mn_owner(PDO $pdo,int $id,string $cap): void {
    if (!function_exists('admin_can') || !admin_can($pdo,$id,$cap)) mn_fail('ليس لديك صلاحية هذه العملية.',403);
}
function mn_audit(string $action,string $type,string $id): void {if(function_exists('audit_log'))audit_log($action,$type,$id);}
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if(!in_array($method,['GET','POST'],true))mn_fail('طريقة الطلب غير مسموحة.',405);
$body=json_decode((string)file_get_contents('php://input'),true);
if(!is_array($body)) $body=[];
$action= $method==='POST' ? mn_str($body,'action',60) : (string)($_GET['action']??'status');
if($action==='login'){
    if($method!=='POST')mn_fail('طلب غير صحيح.',405);
    $identifier=mn_str($body,'identifier',150);$password=(string)($body['password']??'');
    if($identifier==='' || $password==='')mn_fail('أدخل بيانات الدخول.',422);
    try {
        $login=authenticate_login(normalize_login_identifier($identifier),$password,false);
        if(empty($login['ok'])) mn_fail(!empty($login['locked'])?'حاول بعد انتهاء مهلة المحاولات.':'بيانات الدخول غير صحيحة أو يتطلب الحساب تحققاً إضافياً.',!empty($login['locked'])?429:401);
        $u=current_user();
        if(!$u || ($u['role']??'')!=='admin' || ($u['status']??'')!=='active'){
            logout_user();mn_fail('هذا الحساب ليس حساب مدير نشط.',403);
        }
        require_once __DIR__.'/../includes/totp.php';
        $pending=admin_totp_enabled(db(),(int)$u['id']);
        if($pending) $_SESSION['admin_2fa_pending']=(int)$u['id'];
        else unset($_SESSION['admin_2fa_pending']);
        mn_reply(['ok'=>true,'two_factor'=>$pending,'name'=>$u['full_name']??'المدير','csrf'=>$pending?'':csrf_token()]);
    } catch(Throwable $e) {error_log('native admin login: '.$e->getMessage());mn_fail('تعذر الدخول إلى خادم المنصة.',503);}
}
$user=current_user();
if(!$user || ($user['role']??'')!=='admin'||($user['status']??'')!=='active')mn_fail('سجّل الدخول بحساب المدير.',401);
$uid=(int)$user['id'];
$pending=(int)($_SESSION['admin_2fa_pending']??0)===$uid;
if($action==='verify_2fa'){
    if($method!=='POST'||!$pending)mn_fail('التحقق غير مطلوب.',409);
    require_once __DIR__.'/../includes/totp.php';
    if(!rate_limit('native_admin_2fa',6,600))mn_fail('محاولات كثيرة. حاول لاحقاً.',429);
    $row=admin_totp_row(db(),$uid);
    $code=preg_replace('/[^0-9]/','',mn_str($body,'code',12))??'';
    if(!$row||empty($row['enabled'])||!totp_verify_code((string)$row['secret'],$code))mn_fail('رمز المصادقة غير صحيح.',401);
    unset($_SESSION['admin_2fa_pending']);$_SESSION['admin_2fa_verified_at']=time();session_regenerate_id(true);
    mn_audit('دخول الإدارة من التطبيق','user',(string)$uid);
    mn_reply(['ok'=>true,'name'=>$user['full_name']??'المدير','csrf'=>csrf_token(),'two_factor'=>false]);
}
if($pending)mn_reply(['ok'=>false,'two_factor'=>true,'message'=>'مطلوب رمز التحقق الثنائي.'],428);
if($action==='status')mn_reply(['ok'=>true,'name'=>$user['full_name']??'المدير','csrf'=>csrf_token(),'two_factor'=>false]);
require_once __DIR__.'/../includes/platform_tools.php';
$pdo=db();
if($method==='POST') {
    if(!verify_csrf($body['csrf']??null))mn_fail('انتهت صلاحية الجلسة؛ حدّث البيانات.',419);
    if(!rate_limit('native_admin_write',120,3600))mn_fail('عدد عمليات كبير؛ حاول لاحقاً.',429);
}
try {
    if($action==='logout'&&$method==='POST'){logout_user();mn_reply(['ok'=>true]);}
    if($action==='dashboard'&&$method==='GET'){
        $counts=[];
        $tables=['students'=>"SELECT COUNT(*) FROM users WHERE role='student'",
        'subscriptions'=>"SELECT COUNT(*) FROM subscriptions WHERE status='active' AND (expires_at IS NULL OR expires_at>NOW())",
        'questions'=>'SELECT COUNT(*) FROM questions WHERE status=1',
        'teachers'=>"SELECT COUNT(*) FROM users WHERE role='teacher'",
        'codes'=>"SELECT COUNT(*) FROM subscription_access_codes WHERE status='available'",
        'ads'=>"SELECT COUNT(*) FROM school_ads WHERE is_enabled=1 AND start_date<=CURDATE() AND end_date>=CURDATE()"];
        foreach($tables as $key=>$sql){try{$counts[$key]=(int)$pdo->query($sql)->fetchColumn();}catch(Throwable $e){$counts[$key]=null;}}
        mn_reply(['ok'=>true,'stats'=>$counts,'name'=>$user['full_name']??'المدير','csrf'=>csrf_token()]);
    }
    if($action==='codes'&&$method==='GET'){
        mn_owner($pdo,$uid,'codes');
        $rows=$pdo->query("SELECT id,months,status,batch_label,valid_until,redeemed_by,redeemed_at,created_at FROM subscription_access_codes ORDER BY id DESC LIMIT 200")->fetchAll(PDO::FETCH_ASSOC);
        mn_reply(['ok'=>true,'items'=>$rows]);
    }
    if($action==='issue_codes'&&$method==='POST'){
        mn_owner($pdo,$uid,'codes');
        $months=mn_int($body,'months');$count=mn_int($body,'count');
        $batch=mn_str($body,'batch',80);if($batch==='')$batch='native_app';
        $expiry=mn_str($body,'valid_until',10);
        if(!in_array($months,[1,3],true)||$count<1||$count>50||!preg_match('/^[a-zA-Z0-9_-]+$/D',$batch)||($expiry!==''&&!mn_date($expiry)))mn_fail('اختر شهر أو 3 أشهر وعدداً من 1 إلى 50.');
        $pdo->beginTransaction();
        $stmt=$pdo->prepare("INSERT INTO subscription_access_codes(code_hash,months,status,batch_label,valid_until) VALUES(?,?,'available',?,?)");
        $codes=[];for($i=0;$i<$count;$i++){
            $hex=strtoupper(bin2hex(random_bytes(12)));
            $stmt->execute([hash('sha256','MW'.$hex),$months,$batch,$expiry===''?null:$expiry.' 23:59:59']);
            $codes[]='MW-'.implode('-',str_split($hex,4));
        }
        $pdo->commit();mn_audit('إنشاء أكواد اشتراك','codes',(string)$count);
        mn_reply(['ok'=>true,'issued'=>$codes,'message'=>'تم إنشاء الأكواد. انسخها الآن، لن تظهر مرة أخرى.']);
    }
    if(in_array($action,['revoke_code','delete_code','edit_code'],true)&&$method==='POST'){
        mn_owner($pdo,$uid,'codes');$id=mn_int($body,'id');if(!$id)mn_fail('الكود غير محدد.');
        if($action==='revoke_code')$sql="UPDATE subscription_access_codes SET status='revoked' WHERE id=? AND status='available'";
        elseif($action==='delete_code')$sql="DELETE FROM subscription_access_codes WHERE id=? AND status='revoked'";
        else {
            $batch=mn_required(mn_str($body,'batch',80),'المجموعة');
            $expiry=mn_str($body,'valid_until',10);
            if(!preg_match('/^[a-zA-Z0-9_-]+$/D',$batch)||($expiry!==''&&!mn_date($expiry)))mn_fail('بيانات الكود غير صالحة.');
            $sql='UPDATE subscription_access_codes SET batch_label=?, valid_until=? WHERE id=? AND status=\'available\'';
        }
        $stmt=$pdo->prepare($sql);
        $stmt->execute($action==='edit_code'?[$batch,$expiry===''?null:$expiry.' 23:59:59',$id]:[$id]);
        if($stmt->rowCount()===0)mn_fail('لم تُجرَ العملية؛ تأكد أن الكود متاح أو ملغى بحسب الإجراء.',409);
        mn_audit($action,'access_code',(string)$id);mn_reply(['ok'=>true,'message'=>'تم الحفظ بنجاح.']);
    }
    if($action==='ads'&&$method==='GET'){
        mn_owner($pdo,$uid,'school_ads');
        $items=$pdo->query("SELECT id,school_name,ad_text,image_path,start_date,end_date,days,total_price,is_enabled,impressions FROM school_ads ORDER BY id DESC LIMIT 200")->fetchAll(PDO::FETCH_ASSOC);
        mn_reply(['ok'=>true,'items'=>$items]);
    }
    if(in_array($action,['create_ad','edit_ad','toggle_ad','delete_ad'],true)&&$method==='POST'){
        mn_owner($pdo,$uid,'school_ads');$id=mn_int($body,'id');
        if($action==='toggle_ad'||$action==='delete_ad'){
            if(!$id)mn_fail('الإعلان غير محدد.');
            $stmt=$pdo->prepare($action==='toggle_ad'?'UPDATE school_ads SET is_enabled=1-is_enabled WHERE id=?':'DELETE FROM school_ads WHERE id=?');$stmt->execute([$id]);
        }else{
            $school=mn_required(mn_str($body,'school_name',180),'اسم المدرسة');$text=mn_required(mn_str($body,'ad_text',500),'نص الإعلان');
            $from=mn_str($body,'start_date',10);$to=mn_str($body,'end_date',10);
            if(!mn_date($from)||!mn_date($to)||$to<$from)mn_fail('تواريخ الإعلان غير صحيحة.');
            $days=(int)(new DateTimeImmutable($from))->diff(new DateTimeImmutable($to))->days+1;
            if($days<1||$days>366)mn_fail('مدة الإعلان غير صحيحة.');
            $image=mn_str($body,'image_path',255);
            if($image!==''&&(!preg_match('~^/[A-Za-z0-9_./-]+$~D',$image)||str_contains($image,'..')))mn_fail('مسار الصورة غير صالح.');
            if($action==='create_ad'){
                $stmt=$pdo->prepare("INSERT INTO school_ads(school_name,ad_text,image_path,start_date,end_date,days,daily_price,total_price,is_enabled,created_by) VALUES(?,?,?,?,?,?,5000,?,1,?)");
                $stmt->execute([$school,$text,$image===''?null:$image,$from,$to,$days,5000*$days,$uid]);
            }else{
                if(!$id)mn_fail('الإعلان غير محدد.');
                $stmt=$pdo->prepare("UPDATE school_ads SET school_name=?,ad_text=?,image_path=?,start_date=?,end_date=?,days=?,daily_price=5000,total_price=? WHERE id=?");
                $stmt->execute([$school,$text,$image===''?null:$image,$from,$to,$days,5000*$days,$id]);
            }
        }
        if($stmt->rowCount()===0)mn_fail('الإعلان غير موجود أو لم يتغير.',404);
        mn_audit($action,'school_ad',(string)($id?:$pdo->lastInsertId()));mn_reply(['ok'=>true,'message'=>'تم تحديث الإعلان.']);
    }
    if($action==='students'&&$method==='GET'){
        mn_owner($pdo,$uid,'users');$search=trim((string)($_GET['q']??''));
        $search=mb_substr($search,0,80);
        $stmt=$pdo->prepare("SELECT u.id,u.full_name,u.email,u.phone,u.status,g.name grade_name,
           s.status subscription_status,s.expires_at FROM users u
           LEFT JOIN grades g ON g.id=u.grade_id LEFT JOIN subscriptions s ON s.user_id=u.id
           WHERE u.role='student' AND (?='' OR u.full_name LIKE ? OR u.phone LIKE ? OR u.email LIKE ?)
           ORDER BY u.id DESC LIMIT 100");
        $like='%'.$search.'%';$stmt->execute([$search,$like,$like,$like]);
        mn_reply(['ok'=>true,'items'=>$stmt->fetchAll(PDO::FETCH_ASSOC)]);
    }
    if($action==='toggle_student'&&$method==='POST'){
        mn_owner($pdo,$uid,'users');$id=mn_int($body,'id');if(!$id||$id===$uid)mn_fail('الحساب غير صحيح.');
        $stmt=$pdo->prepare("UPDATE users SET status=CASE WHEN status='active' THEN 'blocked' ELSE 'active' END WHERE id=? AND role='student'");
        $stmt->execute([$id]);if($stmt->rowCount()!==1)mn_fail('الطالب غير موجود.',404);
        mn_audit('تغيير حالة الطالب','user',(string)$id);mn_reply(['ok'=>true,'message'=>'تم تعديل حالة الطالب.']);
    }
    if($action==='subscriptions'&&$method==='GET'){
        mn_owner($pdo,$uid,'subscriptions');
        $items=$pdo->query("SELECT u.id,u.full_name,g.name grade_name,s.status,s.expires_at
            FROM users u LEFT JOIN grades g ON g.id=u.grade_id LEFT JOIN subscriptions s ON s.user_id=u.id
            WHERE u.role='student' ORDER BY u.id DESC LIMIT 150")->fetchAll(PDO::FETCH_ASSOC);
        mn_reply(['ok'=>true,'items'=>$items]);
    }
    if($action==='subscription_set'&&$method==='POST'){
        mn_owner($pdo,$uid,'subscriptions');$id=mn_int($body,'id');$months=mn_int($body,'months');$choice=mn_str($body,'mode',12);
        if(!$id||!in_array($choice,['activate','cancel'],true)||($choice==='activate'&&!in_array($months,[1,3],true)))mn_fail('اختيار اشتراك غير صحيح.');
        $check=$pdo->prepare("SELECT id FROM users WHERE id=? AND role='student'");$check->execute([$id]);
        if(!$check->fetchColumn())mn_fail('الطالب غير موجود.',404);
        if($choice==='cancel'){$q=$pdo->prepare("UPDATE subscriptions SET status='cancelled' WHERE user_id=?");$q->execute([$id]);}
        else{
            $amount=$months===1?8000:15000;
            $q=$pdo->prepare("INSERT INTO subscriptions(user_id,amount,status,starts_at,expires_at) VALUES(?,?,'active',NOW(),DATE_ADD(NOW(), INTERVAL {$months} MONTH)) ON DUPLICATE KEY UPDATE amount=VALUES(amount),status='active',starts_at=NOW(),expires_at=DATE_ADD(NOW(), INTERVAL {$months} MONTH)");
            $q->execute([$id,$amount]);
        }
        mn_audit('تعديل اشتراك الطالب','subscription',(string)$id);mn_reply(['ok'=>true,'message'=>'حُفظ الاشتراك.']);
    }
    if($action==='curriculum'&&$method==='GET'){
        mn_owner($pdo,$uid,'curriculum');
        $grades=$pdo->query("SELECT id,name FROM grades ORDER BY sort_order,id")->fetchAll(PDO::FETCH_ASSOC);
        $subjects=$pdo->query("SELECT id,grade_id,name,status,sort_order FROM subjects ORDER BY grade_id,sort_order,id")->fetchAll(PDO::FETCH_ASSOC);
        $chapters=$pdo->query("SELECT id,subject_id,name,status,sort_order FROM chapters ORDER BY subject_id,sort_order,id")->fetchAll(PDO::FETCH_ASSOC);
        mn_reply(['ok'=>true,'grades'=>$grades,'subjects'=>$subjects,'chapters'=>$chapters]);
    }
    if($action==='curriculum_save'&&$method==='POST'){
        mn_owner($pdo,$uid,'curriculum');$type=mn_str($body,'type',20);$name=mn_required(mn_str($body,'name',160),'اسم المادة أو الموضوع');
        $id=mn_int($body,'id');$parent=mn_int($body,'parent_id');$status=mn_int($body,'status')?1:0;
        if(!in_array($type,['subject','chapter'],true)||$parent<1)mn_fail('بيانات الفصل أو المادة غير صحيحة.');
        $table=$type==='subject'?'subjects':'chapters';$fk=$type==='subject'?'grade_id':'subject_id';
        $parentTable=$type==='subject'?'grades':'subjects';
        $chk=$pdo->prepare("SELECT id FROM $parentTable WHERE id=?");$chk->execute([$parent]);if(!$chk->fetchColumn())mn_fail('المادة أو الصف غير موجود.');
        if($id){$stmt=$pdo->prepare("UPDATE $table SET $fk=?,name=?,status=? WHERE id=?");$stmt->execute([$parent,$name,$status,$id]);}
        else{$stmt=$pdo->prepare("INSERT INTO $table($fk,name,status,sort_order) VALUES(?,?,?,9999)");$stmt->execute([$parent,$name,$status]);$id=(int)$pdo->lastInsertId();}
        mn_audit('تعديل المنهج',$type,(string)$id);mn_reply(['ok'=>true,'message'=>'تم حفظ المنهج.']);
    }
    if($action==='questions'&&$method==='GET'){
        mn_owner($pdo,$uid,'questions');$search=mb_substr(trim((string)($_GET['q']??'')),0,80);
        $q=$pdo->prepare("SELECT q.id,q.type,q.question_text,q.correct_answer,q.explanation,q.status,q.verification_status,q.moderation_status,q.chapter_id,c.name chapter_name,s.name subject_name,g.name grade_name
            FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id JOIN grades g ON g.id=s.grade_id
            WHERE (?='' OR q.question_text LIKE ?) ORDER BY q.id DESC LIMIT 100");
        $q->execute([$search,'%'.$search.'%']);mn_reply(['ok'=>true,'items'=>$q->fetchAll(PDO::FETCH_ASSOC)]);
    }
    if($action==='question_save'&&$method==='POST'){
        mn_owner($pdo,$uid,'questions');$id=mn_int($body,'id');
        $text=mn_required(mn_str($body,'question_text',3000),'نص السؤال');
        $answer=mn_required(mn_str($body,'correct_answer',1500),'الإجابة');
        $explanation=mn_str($body,'explanation',5000);
        $chapter=mn_int($body,'chapter_id');
        if($id){
            $chk=$pdo->prepare('SELECT id FROM questions WHERE id=?');$chk->execute([$id]);if(!$chk->fetchColumn())mn_fail('السؤال غير موجود.',404);
            if(function_exists('save_question_revision'))save_question_revision($pdo,$id,$uid,'update');
            $stmt=$pdo->prepare("UPDATE questions SET question_text=?,correct_answer=?,explanation=? WHERE id=?");
            $stmt->execute([$text,$answer,$explanation===''?null:$explanation,$id]);
        }else{
            if(!$chapter)mn_fail('اختر الفصل أولاً.');
            $opts=$body['options']??[];
            if(!is_array($opts)||count($opts)!==4)mn_fail('أضف أربعة اختيارات.');
            $opts=array_map(static fn($v)=>trim((string)$v),$opts);
            if(in_array('',$opts,true)||count(array_unique($opts))!==4||!in_array($answer,$opts,true))mn_fail('الاختيارات يجب أن تكون مختلفة وتتضمن الإجابة الصحيحة.');
            $chk=$pdo->prepare('SELECT c.id FROM chapters c JOIN subjects s ON s.id=c.subject_id WHERE c.id=? AND c.status=1 AND s.status=1');$chk->execute([$chapter]);if(!$chk->fetchColumn())mn_fail('فصل غير متاح.');
            $pdo->beginTransaction();
            $stmt=$pdo->prepare("INSERT INTO questions(chapter_id,type,question_text,correct_answer,explanation,verification_status,moderation_status,status,sort_order) VALUES(?,'mcq',?,?,?,'review','approved',0,9999)");
            $stmt->execute([$chapter,$text,$answer,$explanation===''?null:$explanation]);$id=(int)$pdo->lastInsertId();
            $insert=$pdo->prepare('INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)');
            foreach($opts as $i=>$option)$insert->execute([$id,$option,$i]);
            $pdo->commit();
        }
        mn_audit('حفظ سؤال','question',(string)$id);mn_reply(['ok'=>true,'message'=>'تم حفظ السؤال. الأسئلة الجديدة تبدأ غير منشورة لحين المراجعة.']);
    }
    if(in_array($action,['question_toggle','question_delete'],true)&&$method==='POST'){
        mn_owner($pdo,$uid,'questions');$id=mn_int($body,'id');if(!$id)mn_fail('السؤال غير محدد.');
        if(function_exists('save_question_revision'))save_question_revision($pdo,$id,$uid,$action==='question_delete'?'delete':'update');
        $stmt=$pdo->prepare($action==='question_toggle'?'UPDATE questions SET status=1-status WHERE id=?':'DELETE FROM questions WHERE id=?');$stmt->execute([$id]);
        if($stmt->rowCount()===0)mn_fail('السؤال غير موجود.',404);
        mn_audit($action,'question',(string)$id);mn_reply(['ok'=>true,'message'=>'تم تحديث السؤال.']);
    }
    mn_fail('العملية غير موجودة.',404);
} catch (Throwable $e) {
    if($pdo->inTransaction())$pdo->rollBack();
    error_log('native admin API '.$action.': '.$e->getMessage());
    mn_fail('تعذر تنفيذ العملية على قاعدة بيانات الموقع. راجع سجلات الخادم.',503);
}
