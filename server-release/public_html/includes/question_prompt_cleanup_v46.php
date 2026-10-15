<?php
declare(strict_types=1);

/**
 * تنظيف صياغة الأسئلة + موازنة معاني الإسلامية.
 * - يزيل المقدمات العامة من نص السؤال ليظهر السؤال مباشرة.
 * - يجعل «المعاني والمفردات» في الإسلامية 50% اختيارات و50% نصية لكل صف.
 * - آمن للتشغيل المتكرر.
 */

function murshid_question_prompt_prefixes(): array {
    return [
        'أجب عن السؤال الآتي:',
        'أجب بإجابة دقيقة:',
        'اكتب الجواب الصحيح:',
        'سؤال مراجعة:',
        'سؤال تثبيت:',
        'اختبر فهمك:',
        'ضمن هذا الموضوع، أجب:',
        'في مراجعة الوحدة، أجب:',
        'للتدريب، أجب عن الآتي:',
        'اكتب الإجابة المختصرة:',
        'أجب وفق المنهج:',
        'أجب مباشرة:',
        'ركز ثم أجب:',
        'سؤال تطبيقي:',
        'سؤال فهم:',
        'من مراجعة الموضوع:',
        'مراجعة مركزة:',
        'سؤال من صلب الموضوع:',
        'صح أم خطأ:',
    ];
}

function murshid_direct_question_prompt(string $text): string {
    $text=trim($text);
    if($text==='') return '';
    do {
        $before=$text;
        foreach(murshid_question_prompt_prefixes() as $prefix){
            if(str_starts_with($text,$prefix)){
                $text=trim(substr($text,strlen($prefix)));
            }
        }
    } while($text!==$before);
    return $text;
}

function murshid_direct_meaning_prompt(string $text): string {
    $text=murshid_direct_question_prompt($text);
    if(preg_match('/^أكمل المعنى الوزاري\s*\d+\s*:\s*المقصود بـ[«"](.+?)[»"]\s*هو\s*_+[.]?$/u',$text,$m)){
        return 'ما معنى «'.trim((string)$m[1]).'»؟';
    }
    if(preg_match('/^المقصود بـ[«"](.+?)[»"]\s*هو\s*_+[.]?$/u',$text,$m)){
        return 'ما معنى «'.trim((string)$m[1]).'»؟';
    }
    return $text;
}

function murshid_meaning_word_from_prompt(string $text): string {
    if(preg_match('/[«"]([^»"]+)[»"]/u',$text,$m)) return trim((string)$m[1]);
    if(preg_match('/ما معنى(?: كلمة)?\s+(.+?)[؟?]?$/u',$text,$m)) return rtrim(trim((string)$m[1]),"؟?");
    return '';
}

function murshid_meaning_options(array $pool,string $correct,string $salt): array {
    $candidates=[];
    foreach($pool as $value){
        $value=trim((string)$value);
        if($value!=='' && $value!==$correct && !in_array($value,$candidates,true)) $candidates[]=$value;
    }
    usort($candidates,static function($a,$b)use($salt){
        return strcmp(hash('sha256',$salt.'|'.$a),hash('sha256',$salt.'|'.$b));
    });
    $opts=array_slice($candidates,0,3);
    if($correct!=='' && !in_array($correct,$opts,true)) $opts[]=$correct;
    usort($opts,static function($a,$b)use($salt){
        return strcmp(hash('sha256','opt|'.$salt.'|'.$a),hash('sha256','opt|'.$salt.'|'.$b));
    });
    return array_slice(array_values(array_unique($opts)),0,4);
}

/**
 * يوازن موضوع معاني واحد حسب ترتيب السؤال الثابت:
 * 0,2,4... اختيارات / 1,3,5... إجابة نصية.
 */
function murshid_balance_islamic_meaning_chapter(PDO $pdo,int $chapterId): array {
    $stats=['total'=>0,'mcq'=>0,'text'=>0,'cleaned'=>0];
    if($chapterId<=0) return $stats;

    $q=$pdo->prepare("SELECT id,question_text,correct_answer FROM questions WHERE chapter_id=? AND COALESCE(status,1)=1 ORDER BY id");
    $q->execute([$chapterId]);
    $rows=$q->fetchAll(PDO::FETCH_ASSOC);
    if(!$rows) return $stats;

    $pool=[];
    foreach($rows as $row){
        $answer=trim((string)($row['correct_answer']??''));
        if($answer!=='' && !in_array($answer,$pool,true)) $pool[]=$answer;
    }

    $qcols=[];
    try{foreach($pdo->query("SHOW COLUMNS FROM questions")->fetchAll(PDO::FETCH_ASSOC) as $c)$qcols[(string)$c['Field']]=true;}catch(Throwable $e){}
    $hasAnswers=false;
    try{$hasAnswers=(bool)$pdo->query("SHOW TABLES LIKE 'answers'")->fetchColumn();}catch(Throwable $e){}
    $deleteAnswers=$hasAnswers?$pdo->prepare('DELETE FROM answers WHERE question_id=?'):null;
    $insertAnswer=$hasAnswers?$pdo->prepare('INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)'):null;

    foreach($rows as $index=>$row){
        $qid=(int)$row['id'];
        $old=trim((string)($row['question_text']??''));
        $question=murshid_direct_meaning_prompt($old);
        $correct=trim((string)($row['correct_answer']??''));
        $isChoice=($index%2===0);
        $type=$isChoice?'mcq':'text';
        $word=murshid_meaning_word_from_prompt($question);

        $sets=['type=?','question_text=?'];$vals=[$type,$question];
        if(isset($qcols['meaning_word'])){$sets[]='meaning_word=?';$vals[]=$word!==''?$word:null;}
        $vals[]=$qid;
        $pdo->prepare('UPDATE questions SET '.implode(',',$sets).' WHERE id=?')->execute($vals);
        if($question!==$old)$stats['cleaned']++;

        if($deleteAnswers)$deleteAnswers->execute([$qid]);
        if($isChoice && $insertAnswer){
            $opts=murshid_meaning_options($pool,$correct,(string)$qid);
            foreach($opts as $i=>$opt)$insertAnswer->execute([$qid,$opt,$i+1]);
            $stats['mcq']++;
        }else{
            $stats['text']++;
        }
        $stats['total']++;
    }
    return $stats;
}

/** تطبيق تلقائي مرة واحدة على قاعدة الموقع الحالية. */
function murshid_apply_question_prompt_cleanup_v46_once(): void {
    $marker=__DIR__.'/../storage/.question_prompt_cleanup_v46_done';
    if(is_file($marker)) return;

    try{$pdo=db();}catch(Throwable $e){return;}
    try{
        $hasQuestions=$pdo->query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions'")->fetchColumn();
        if(!(int)$hasQuestions) return;
        $pdo->exec("CREATE TABLE IF NOT EXISTS content_migrations (code VARCHAR(120) PRIMARY KEY,details VARCHAR(255) NULL,completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $code='direct_questions_islamic_meanings_50_50_20260925';
        $check=$pdo->prepare('SELECT 1 FROM content_migrations WHERE code=? LIMIT 1');$check->execute([$code]);
        if($check->fetchColumn()){
            @file_put_contents($marker,'done');
            return;
        }

        $locked=true;
        try{$l=$pdo->prepare('SELECT GET_LOCK(?,0)');$l->execute(['murshid_question_prompt_cleanup_v46']);$v=$l->fetchColumn();$locked=$v===null||$v===false?true:(int)$v===1;}catch(Throwable $e){}
        if(!$locked) return;

        // إزالة العبارات التمهيدية العامة من كل أسئلة الموقع. نكرر ثلاث مرات لأن بعض السجلات تحتوي مقدمتين متتاليتين.
        $update=$pdo->prepare("UPDATE questions SET question_text=TRIM(SUBSTRING(question_text,CHAR_LENGTH(?) + 1)) WHERE question_text LIKE ?");
        for($round=0;$round<3;$round++){
            foreach(murshid_question_prompt_prefixes() as $prefix){
                $update->execute([$prefix,$prefix.'%']);
            }
        }

        // موازنة الإسلامية لكل الصفوف الموجودة في المنصة.
        $chapter=$pdo->prepare("SELECT c.id FROM chapters c JOIN subjects s ON s.id=c.subject_id JOIN grades g ON g.id=s.grade_id WHERE g.name=? AND s.name='التربية الإسلامية' AND c.name='المعاني والمفردات' ORDER BY c.id LIMIT 1");
        $parts=[];
        foreach(['الثالث المتوسط','السادس العلمي','السادس الأدبي'] as $grade){
            $chapter->execute([$grade]);$cid=(int)$chapter->fetchColumn();
            if($cid>0){
                $s=murshid_balance_islamic_meaning_chapter($pdo,$cid);
                $parts[]=$grade.':'.$s['mcq'].' اختيار/'.$s['text'].' نصي';
            }
        }

        $details='Direct prompts; Islamic meanings 50/50; '.implode(' | ',$parts);
        $pdo->prepare("INSERT INTO content_migrations(code,details,completed_at) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE details=VALUES(details),completed_at=NOW()")
            ->execute([$code,substr($details,0,255)]);
        @file_put_contents($marker,'done');
        try{$u=$pdo->prepare('SELECT RELEASE_LOCK(?)');$u->execute(['murshid_question_prompt_cleanup_v46']);}catch(Throwable $e){}
    }catch(Throwable $e){
        error_log('question prompt cleanup v46: '.$e->getMessage());
        try{$u=$pdo->prepare('SELECT RELEASE_LOCK(?)');$u->execute(['murshid_question_prompt_cleanup_v46']);}catch(Throwable $ignore){}
    }
}
