<?php
declare(strict_types=1);
/** Actual platform functions against a disposable database. No external delivery or money. */
function check(bool $ok,string $message): void { if(!$ok)throw new RuntimeException($message); }
function connection(): PDO {
    return new PDO((string)getenv('RELEASE_TEST_DSN'),'root','synthetic-ci-only',[
        PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
}
$pdo=connection();
function db(): PDO { return $GLOBALS['pdo']; }
check($pdo->query('SELECT DATABASE()')->fetchColumn()==='murshid_release_test','Disposable test database required');
require_once __DIR__.'/../public_html/includes/functions.php';
require_once __DIR__.'/../public_html/includes/android_school_fcm.php';
require_once __DIR__.'/../public_html/includes/android_fcm.php';
require_once __DIR__.'/../public_html/includes/school_ad_apns.php';

$pdo->exec("CREATE TABLE users(id INT UNSIGNED PRIMARY KEY,grade_id INT NOT NULL DEFAULT 1,role VARCHAR(20) DEFAULT 'student',status VARCHAR(20) DEFAULT 'active',created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE subjects(id INT PRIMARY KEY,grade_id INT,status INT DEFAULT 1) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE chapters(id INT PRIMARY KEY,subject_id INT,status INT DEFAULT 1) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE questions(id INT PRIMARY KEY,chapter_id INT,status INT DEFAULT 1,moderation_status VARCHAR(20) DEFAULT 'approved',question_text TEXT,correct_answer TEXT) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE answer_events(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,user_id INT UNSIGNED,question_id INT,chapter_id INT,is_correct TINYINT,answer_text TEXT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uq_answer(user_id,question_id)) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE subscriptions(id INT PRIMARY KEY,user_id INT,status VARCHAR(20),expires_at DATETIME NULL) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE notifications(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,user_id INT UNSIGNED,title TEXT,body TEXT,is_read TINYINT DEFAULT 0) ENGINE=InnoDB");
$pdo->exec("INSERT INTO users(id) VALUES(101),(102),(103)");
$pdo->exec("INSERT INTO subjects(id,grade_id) VALUES(1,1),(2,2)");
$pdo->exec("INSERT INTO chapters(id,subject_id) VALUES(1,1),(2,2)");
$insert=$pdo->prepare('INSERT INTO questions(id,chapter_id,question_text,correct_answer) VALUES(?,?,?,?)');
for($i=1;$i<=18;$i++)$insert->execute([$i,1,'سؤال اصطناعي '.$i,'8']);
$insert->execute([99,2,'صف مختلف','8']);
check(free_answers_used(101)===0,'Opening content must not consume free quota');
for($i=1;$i<=14;$i++)check(record_answer_event_with_quota(101,$i,1,true,'8')===1,'First acceptance failed');
check(record_answer_event_with_quota(101,1,1,false,'9')===0,'Duplicate accepted twice');
$stored=$pdo->query('SELECT answer_text,is_correct FROM answer_events WHERE user_id=101 AND question_id=1')->fetch();
check($stored['answer_text']==='8'&&(int)$stored['is_correct']===1,'Retry changed the first immutable answer');
check(free_answers_used(101)===14,'Duplicate consumed a free answer');

if(function_exists('pcntl_fork')) {
    $files=[];$children=[];
    foreach([15,16] as $qid){
        $file=tempnam(sys_get_temp_dir(),'quota-');$files[]=$file;$pid=pcntl_fork();
        if($pid===0){$GLOBALS['pdo']=connection();file_put_contents($file,(string)record_answer_event_with_quota(101,$qid,1,true,'8'));exit(0);}
        check($pid>0,'Could not fork quota test');$children[]=$pid;
    }
    foreach($children as $child)pcntl_waitpid($child,$status);
    $results=array_map(static fn($f)=>(int)file_get_contents($f),$files);sort($results);
    foreach($files as $file)unlink($file);
    $GLOBALS['pdo']=$pdo=connection();
    check($results===[-1,1],'Concurrent submissions exceeded 15 free answers');
}else{check(record_answer_event_with_quota(101,15,1,true,'8')===1,'Fifteenth answer failed');}
check(free_answers_used(101)===15,'Exactly fifteen free answers required');
check(record_answer_event_with_quota(101,17,1,true,'8')===-1,'Expired/free account bypassed quota');
$pdo->exec("INSERT INTO subscriptions VALUES(1,101,'active',DATE_ADD(NOW(),INTERVAL 1 DAY))");
check(is_subscribed(101),'Active subscription not recognized');
$expectedUnix=(int)$pdo->query('SELECT UNIX_TIMESTAMP(expires_at) FROM subscriptions WHERE id=1')->fetchColumn();
check(abs($expectedUnix-(time()+86400))<=3,'Offline expiry must be a Unix timestamp in the database session time zone');
check(record_answer_event_with_quota(101,17,1,true,'8')===1,'Paid account incorrectly blocked');
$pdo->exec("UPDATE subscriptions SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND)");
check(!is_subscribed(101),'Expired subscription still active');
check(record_answer_event_with_quota(101,18,1,true,'8')===-1,'Expired subscription granted content');
try{record_answer_event_with_quota(102,99,2,true,'8');throw new RuntimeException('Cross-grade question accepted');}
catch(RuntimeException $e){check($e->getMessage()==='Question unavailable for student grade','Wrong cross-grade rejection');}
echo "PASS actual quota functions: grade binding, first receipt, concurrent limit, paid activation and expiry\n";

check(grade_arabic_answer('علي.','علي')['is_correct'],'Arabic punctuation rejected');
check(murshid_math_equivalent('2*x','x+x')===true,'Equivalent algebra rejected');
check(murshid_math_equivalent('(x-3)*(x+3)','x^2-9')===true,'Factorized expression rejected');
check(murshid_math_equivalent('x+3','x+2')===false,'Wrong algebra accepted');
check(murshid_math_value('2^3^2')===512.0,'Exponent associativity incorrect');
check(murshid_math_value('1/0')===null,'Division by zero accepted');
check(murshid_math_value('5 garbage')===null,'Trailing garbage accepted');
check(murshid_math_number_with_unit('100 cm','m')===1.0,'Unit conversion incorrect');
check(murshid_math_number_with_unit('100 cm wrong','m')===null,'Trailing unit garbage accepted');
check(murshid_quran_same('قل هو الله احد','قُلْ هُوَ اللَّهُ أَحَدٌ'),'Unvocalized transcription rejected');
check(!murshid_quran_same('قل الله هو احد','قل هو الله احد'),'Reordered verse accepted');
check(!murshid_quran_same('قل هو الله','قل هو الله احد'),'Incomplete verse accepted');
echo "PASS actual Arabic, Quran transcription, algebra, roots, numeric parsing and units\n";

$pdo->exec("CREATE TABLE school_ads(id BIGINT UNSIGNED PRIMARY KEY,school_name VARCHAR(180),ad_text TEXT,start_date DATE,end_date DATE,start_at DATETIME,end_at DATETIME,is_enabled TINYINT DEFAULT 1,max_impressions INT DEFAULT 0,impressions INT DEFAULT 0,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
android_fcm_schema($pdo);android_school_fcm_install($pdo);
$pdo->exec(file_get_contents(__DIR__.'/../migration/060_school_ad_apns.sql'));
$pdo->exec("INSERT INTO school_ads(id,school_name,ad_text,start_at,end_at) VALUES(1,'مدرسة اختبار','إعلان اصطناعي',DATE_SUB(NOW(),INTERVAL 1 MINUTE),DATE_ADD(NOW(),INTERVAL 1 DAY))");
$binding='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
android_fcm_register($pdo,102,['installation_id'=>'11111111-1111-1111-1111-111111111111','binding_id'=>$binding,'token'=>str_repeat('synthetic-token-',10)]);
check(android_school_fcm_tick($pdo)['queued']===1,'New school ad not queued');
check(android_school_fcm_tick($pdo)['queued']===0,'School ad queued twice');
check(murshid_school_push_enqueue($pdo,1),'APNs event not queued');
check(murshid_school_push_enqueue($pdo,1),'APNs recheck failed');
check((int)$pdo->query('SELECT COUNT(*) FROM murshid_school_push_events')->fetchColumn()===1,'Duplicate pending APNs event');
$pdo->exec('UPDATE android_fcm_devices SET next_attempt_at=NOW()');
$sent=[];
$result=android_fcm_dispatch($pdo,40,static function(array $device,int $id)use(&$sent):array{$sent[]=android_fcm_payload($device,$id);return ['ok'=>true];});
check($result['sent']===1&&($sent[0]['message']['data']['type']??'')==='school_ad','School-ad routing lost');
check(!isset($sent[0]['message']['notification']),'Private inbox content leaked into FCM alert');
echo "PASS school ads: one event, Android routing, protected account binding; no external push sent\n";
