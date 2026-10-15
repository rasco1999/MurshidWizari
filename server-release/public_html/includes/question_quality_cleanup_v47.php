<?php
declare(strict_types=1);

/**
 * v47 — تنظيف جودة بنك الأسئلة للطالب.
 *
 * الهدف:
 * - إيقاف أسئلة التحقق الداخلية/الاختبارية التي تسربت إلى واجهة الطالب.
 * - إزالة عبارات التحرير مثل «استرجاع وزاري 12» و«إجابة مركزة 3» من نص السؤال.
 * - منع تكرار السؤال نفسه حرفياً بعد التنظيف مع إبقاء النسخة الأعلى جودة.
 * - ضمان أن أسئلة الصح/الخطأ تملك خياري «صح / خطأ» بدل ظهور حقل نصي.
 * - حذف الشروح العامة التي لا تشرح الإجابة فعلياً.
 *
 * لا نحذف سجلات من قاعدة البيانات؛ النسخ الرديئة/المكررة تُعطّل status=0 فقط
 * حتى تبقى answer_events والمراجع القديمة سليمة.
 */

function murshid_v47_is_internal_validation_prompt(string $text): bool {
    $text=trim($text);
    if($text==='') return true;
    if(preg_match('/^\s*تحقق\s+بصري\b.*(?:v\s*25|v25)/iu',$text)) return true;
    if(preg_match('/(?:v\s*25|v25).*تحقق\s+بصري/iu',$text)) return true;
    if(preg_match('/\bهل\s+الإجابة\s+[«"“][^»"”]+[»"”]\s+صحيحة\s*[؟?]?\s*$/u',$text)) return true;
    if(preg_match('/^\s*(?:صح\s+أم\s+خطأ\s*:\s*)?الإجابة\s+عن\s+[«"“].+?[»"”]\s+هي\s+[«"“].+?[»"”]\s*[.。؟?]?\s*$/u',$text)) return true;
    if(str_contains($text,'تدريب تحقق وزاري:')) return true;
    return false;
}

function murshid_v47_clean_prompt(string $text): string {
    $text=trim($text);
    if($text==='') return '';

    // صياغات «عرّف» يجب ألا تتحول إلى اسم مفرد بلا فعل سؤال.
    if(preg_match('/^\s*عرّف\s+المصطلح\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*(.+)$/u',$text,$m)){
        $tail=trim((string)$m[1]);
        return $tail!=='' ? 'عرّف '.$tail : $text;
    }

    // صيغة المعاني تتحول إلى سؤال مباشر بدلاً من فراغ اصطناعي.
    if(preg_match('/^\s*أكمل\s+المعنى\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*المقصود\s+بـ?[«"“](.+?)[»"”]\s+هو\s*_+[.。]?\s*$/u',$text,$m)){
        return 'ما معنى «'.trim((string)$m[1]).'»؟';
    }

    $patterns=[
        '/^\s*استرجاع\s+وزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*وزاري(?:\s+[\p{N}٠-٩]{2,4})?(?:\s*[\/\-–—]\s*[^:：\n]{1,48})?\s*[:：]\s*/u',
        '/^\s*(?:سؤال\s+)?(?:تدريب|تدريبي|تدريبية)(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*(?:سؤال\s+)?مراجعة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*اختيار\s+تحليلي(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*إجابة\s+مركزة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*/u',
        '/^\s*تحقق\s+بصري(?:\s+[\p{N}٠-٩]{1,4})?\s*[\-–—]?\s*(?:v\s*25|v25)?\s*[:：]\s*/iu',
    ];
    foreach($patterns as $pattern){
        $clean=preg_replace($pattern,'',$text,1,$count);
        if($count>0){$text=trim((string)$clean);break;}
    }
    $text=preg_replace('/[ \t]{2,}/u',' ',$text)??$text;
    return trim($text);
}

function murshid_v47_generic_explanation(string $text): bool {
    $text=trim($text);
    if($text==='') return true;
    $patterns=[
        '/^تدريب\s+مشتق\s+من\s+مفهوم\s+موثق\s+في\s+هذا\s+المحور/u',
        '/^تدريب\s+مشتق\s+موثق/u',
        '/^هذا\s+تدريب\s+(?:تحقق\s+)?مشتق/u',
        '/^صيغة\s+تدريب\s+جديدة\s+مشتقة/u',
        '/^راجع\s+الإجابة\s+النموذجية/u',
    ];
    foreach($patterns as $p) if(preg_match($p,$text)) return true;
    return false;
}

function murshid_v47_quality_score(array $row): int {
    $score=0;
    $type=(string)($row['type']??'');
    $optionCount=(int)($row['option_count']??0);
    if($type==='mcq' && $optionCount>=2) $score+=80;
    elseif($optionCount>=2) $score+=45;
    if(in_array($type,['calculation','genetics','match'],true)) $score+=100;
    $exp=trim((string)($row['explanation']??''));
    if($exp!=='' && !murshid_v47_generic_explanation($exp)) $score+=55;
    if((string)($row['verification_status']??'')==='verified') $score+=30;
    if(!empty($row['exam_year'])) $score+=20;
    $raw=trim((string)($row['question_text']??''));
    if($raw===murshid_v47_clean_prompt($raw)) $score+=15;
    // عند التعادل نميل للسجل الأقدم لأنه غالباً المصدر الأصلي لا النسخة المولدة لاحقاً.
    $id=(int)($row['id']??PHP_INT_MAX);
    $score+=max(0,10-min(10,(int)floor($id/100000)));
    return $score;
}

function murshid_apply_question_quality_cleanup_v47_once(): void {
    $marker=__DIR__.'/../storage/.question_quality_cleanup_v47_done';
    if(is_file($marker)) return;

    try{$pdo=db();}catch(Throwable $e){return;}
    $lockName='murshid_question_quality_cleanup_v47';
    $locked=false;
    try{
        $hasQuestions=(int)$pdo->query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions'")->fetchColumn();
        if(!$hasQuestions) return;
        $pdo->exec("CREATE TABLE IF NOT EXISTS content_migrations (code VARCHAR(120) PRIMARY KEY,details VARCHAR(255) NULL,completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $code='question_quality_v47_20261007';
        $check=$pdo->prepare('SELECT 1 FROM content_migrations WHERE code=? LIMIT 1');$check->execute([$code]);
        if($check->fetchColumn()){@file_put_contents($marker,'done');return;}

        try{$l=$pdo->prepare('SELECT GET_LOCK(?,0)');$l->execute([$lockName]);$v=$l->fetchColumn();$locked=((int)$v===1);}catch(Throwable $e){$locked=true;}
        if(!$locked) return;

        $q=$pdo->query("SELECT q.id,q.chapter_id,q.type,q.question_text,q.correct_answer,q.explanation,q.verification_status,q.exam_year,q.status,(SELECT COUNT(*) FROM answers a WHERE a.question_id=q.id) option_count FROM questions q ORDER BY q.chapter_id,q.sort_order,q.id");
        $rows=$q->fetchAll(PDO::FETCH_ASSOC)?:[];
        $deactivate=$pdo->prepare('UPDATE questions SET status=0 WHERE id=?');
        $updatePrompt=$pdo->prepare('UPDATE questions SET question_text=? WHERE id=?');
        $updateExp=$pdo->prepare('UPDATE questions SET explanation=NULL WHERE id=?');
        $disabledMeta=0;$cleaned=0;$clearedExp=0;

        // 1) أوقف نصوص التحقق الداخلي ونظف بقية الصياغات.
        foreach($rows as &$row){
            if((int)($row['status']??1)!==1) continue;
            $id=(int)$row['id'];$raw=trim((string)($row['question_text']??''));
            if(murshid_v47_is_internal_validation_prompt($raw)){
                $deactivate->execute([$id]);$row['status']=0;$disabledMeta++;continue;
            }
            $clean=murshid_v47_clean_prompt($raw);
            if($clean!=='' && $clean!==$raw){$updatePrompt->execute([$clean,$id]);$row['question_text']=$clean;$cleaned++;}
            $exp=trim((string)($row['explanation']??''));
            if($exp!=='' && murshid_v47_generic_explanation($exp)){$updateExp->execute([$id]);$row['explanation']=null;$clearedExp++;}
        }
        unset($row);

        // 2) إزالة التكرار الحرفي داخل الموضوع نفسه بعد التنظيف، مع إبقاء النسخة الأعلى جودة.
        $groups=[];
        foreach($rows as $row){
            if((int)($row['status']??1)!==1) continue;
            if(in_array((string)($row['type']??''),['match','genetics','calculation'],true)) continue;
            $prompt=trim(preg_replace('/\s+/u',' ',murshid_v47_clean_prompt((string)($row['question_text']??'')))??'');
            $answer=public_question_compare_key((string)($row['correct_answer']??''));
            if($prompt===''||$answer==='') continue;
            $key=(int)$row['chapter_id']."\0".$prompt."\0".$answer;
            $groups[$key][]=$row;
        }
        $disabledDup=0;
        foreach($groups as $same){
            if(count($same)<2) continue;
            usort($same,static function(array $a,array $b): int {
                $sa=murshid_v47_quality_score($a);$sb=murshid_v47_quality_score($b);
                return $sa===$sb ? ((int)$a['id']<=> (int)$b['id']) : ($sb<=>$sa);
            });
            $keep=(int)$same[0]['id'];
            foreach(array_slice($same,1) as $dup){
                $id=(int)$dup['id'];if($id===$keep)continue;
                $deactivate->execute([$id]);$disabledDup++;
            }
        }

        // 3) أي صح/خطأ منشور يجب أن يظهر كاختيارات لا كحقل نصي.
        $answerHas=$pdo->prepare('SELECT COUNT(*) FROM answers WHERE question_id=? AND answer_text=?');
        $answerIns=$pdo->prepare('INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)');
        $tf=$pdo->query("SELECT id FROM questions WHERE status=1 AND type='true_false'")->fetchAll(PDO::FETCH_COLUMN)?:[];
        $tfAdded=0;
        foreach($tf as $qid){
            $qid=(int)$qid;
            foreach([['صح',1],['خطأ',2]] as [$label,$order]){
                $answerHas->execute([$qid,$label]);
                if(!(int)$answerHas->fetchColumn()){$answerIns->execute([$qid,$label,$order]);$tfAdded++;}
            }
        }

        $details="meta={$disabledMeta}; dup={$disabledDup}; prompt={$cleaned}; exp={$clearedExp}; tf={$tfAdded}";
        $pdo->prepare("INSERT INTO content_migrations(code,details,completed_at) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE details=VALUES(details),completed_at=NOW()")
            ->execute([$code,substr($details,0,255)]);
        @file_put_contents($marker,'done');
    }catch(Throwable $e){
        error_log('question quality cleanup v47: '.$e->getMessage());
    }finally{
        if($locked){try{$u=$pdo->prepare('SELECT RELEASE_LOCK(?)');$u->execute([$lockName]);}catch(Throwable $ignore){}}
    }
}
