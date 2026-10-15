<?php
declare(strict_types=1);

/**
 * تنظيف التوضيحات المكررة من قاعدة البيانات الحالية.
 * يعمل مرة واحدة فقط، مع بقاء فلتر العرض كحماية دائمة.
 */
function murshid_apply_explanation_cleanup_v46_once(): void {
    $marker=__DIR__.'/../storage/.question_explanation_cleanup_v46_done';
    if(is_file($marker)) return;

    try{$pdo=db();}catch(Throwable $e){return;}
    try{
        $exists=(int)$pdo->query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions'")->fetchColumn();
        if(!$exists) return;
        $pdo->exec("CREATE TABLE IF NOT EXISTS content_migrations (code VARCHAR(120) PRIMARY KEY,details VARCHAR(255) NULL,completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $code='dedupe_question_explanations_20260925';
        $check=$pdo->prepare('SELECT 1 FROM content_migrations WHERE code=? LIMIT 1');
        $check->execute([$code]);
        if($check->fetchColumn()){@file_put_contents($marker,'done');return;}

        $locked=true;
        try{$l=$pdo->prepare('SELECT GET_LOCK(?,0)');$l->execute(['murshid_explanation_cleanup_v46']);$v=$l->fetchColumn();$locked=$v===null||$v===false?true:(int)$v===1;}catch(Throwable $e){}
        if(!$locked) return;

        $q=$pdo->query("SELECT id,correct_answer,explanation FROM questions WHERE explanation IS NOT NULL AND TRIM(explanation)<>''");
        $update=$pdo->prepare('UPDATE questions SET explanation=? WHERE id=?');
        $changed=0;$cleared=0;
        foreach($q->fetchAll(PDO::FETCH_ASSOC)?:[] as $row){
            $old=trim((string)($row['explanation']??''));
            $clean=public_question_explanation($old,(string)($row['correct_answer']??''));
            if($clean===$old) continue;
            $update->execute([$clean!==''?$clean:null,(int)$row['id']]);
            $changed++;if($clean==='')$cleared++;
        }

        // نفس الحماية للأسئلة المولدة إن كان الجدول موجوداً.
        try{
            $hasAi=(bool)$pdo->query("SHOW TABLES LIKE 'ai_generated_questions'")->fetchColumn();
            if($hasAi){
                $q2=$pdo->query("SELECT id,correct_answer,explanation FROM ai_generated_questions WHERE explanation IS NOT NULL AND TRIM(explanation)<>''");
                $u2=$pdo->prepare('UPDATE ai_generated_questions SET explanation=? WHERE id=?');
                foreach($q2->fetchAll(PDO::FETCH_ASSOC)?:[] as $row){
                    $old=trim((string)($row['explanation']??''));
                    $clean=public_question_explanation($old,(string)($row['correct_answer']??''));
                    if($clean===$old) continue;
                    $u2->execute([$clean!==''?$clean:null,(int)$row['id']]);
                    $changed++;if($clean==='')$cleared++;
                }
            }
        }catch(Throwable $ignore){}

        $details='Removed duplicate answer/explanation text: '.$cleared.'; changed: '.$changed;
        $pdo->prepare("INSERT INTO content_migrations(code,details,completed_at) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE details=VALUES(details),completed_at=NOW()")
            ->execute([$code,substr($details,0,255)]);
        @file_put_contents($marker,'done');
        try{$u=$pdo->prepare('SELECT RELEASE_LOCK(?)');$u->execute(['murshid_explanation_cleanup_v46']);}catch(Throwable $e){}
    }catch(Throwable $e){
        error_log('question explanation cleanup v46: '.$e->getMessage());
        try{$u=$pdo->prepare('SELECT RELEASE_LOCK(?)');$u->execute(['murshid_explanation_cleanup_v46']);}catch(Throwable $ignore){}
    }
}
