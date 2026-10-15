<?php
declare(strict_types=1);

/**
 * v48 — طبقة سلامة وربط السؤال من المصدر حتى التصحيح.
 *
 * المبدأ: لا يكفي أن يكون نص السؤال موجوداً؛ يجب أن يكون نوعه، خياراته،
 * إجابته الصحيحة، ومسار التصحيح متوافقين معاً. أي سؤال غير قابل للإجابة
 * بصورة موثوقة لا يصل إلى الطالب حتى تتم مراجعته.
 */

function murshid_v48_normalize_type(?string $type): string {
    $type=mb_strtolower(trim((string)$type),'UTF-8');
    $map=[
        'multiple_choice'=>'mcq','multiple-choice'=>'mcq','choice'=>'mcq','choices'=>'mcq','select'=>'mcq',
        'truefalse'=>'true_false','true-false'=>'true_false','boolean'=>'true_false','bool'=>'true_false','tf'=>'true_false',
        'fill_blank'=>'fill','fill-blank'=>'fill','blank'=>'fill',
        'essay'=>'text','written'=>'text','short_answer'=>'text','short-answer'=>'text','answer'=>'text',
    ];
    return $map[$type]??($type!==''?$type:'text');
}

function murshid_v48_choice_required_prompt(string $text): bool {
    $text=public_question_display_text($text);
    if($text==='') return false;
    $patterns=[
        '/^(?:اختر|اختاري|حد[دّ]|عي[ّ]?ن)\b/u',
        '/^(?:أي|اي)\s+مما\s+(?:يأتي|ياتي|سبق)\b/u',
        '/^(?:أي|اي)\s+من\s+(?:الآتي|الاتي|التالي|التالية)\b/u',
        '/^(?:أي|اي)\s+(?:كلمة|كلمات|لفظ|لفظة|عبارة|جملة|صيغة|فعل|اسم)\b.*\b(?:مما\s+(?:يأتي|ياتي)|من\s+(?:الآتي|الاتي|التالي|التالية))\b/u',
        '/\b(?:الاختيار|الخيار)\s+الصحيح\b/u',
        '/\bأي\s+(?:الخيارات|الاجابات|الإجابات)\b/u',
    ];
    foreach($patterns as $p) if(preg_match($p,$text)) return true;
    return false;
}

function murshid_v48_true_false_prompt(string $text): bool {
    $text=public_question_display_text($text);
    if($text==='') return false;
    return (bool)(preg_match('/^\s*(?:صح\s*(?:أم|او|أو)\s*خط[أا]|صح\s*او\s*خط[أا])\s*[:：\-–—]?/u',$text)
        || preg_match('/\b(?:صواب\s*(?:أم|او|أو)\s*خط[أا])\b/u',$text));
}

/** @param array<int,array{id?:int,answer_text?:string,text?:string}> $options */
function murshid_v48_unique_options(array $options): array {
    $out=[];$seen=[];$synthetic=-1;
    foreach($options as $row){
        $text=trim((string)($row['answer_text']??$row['text']??''));
        if($text==='') continue;
        $key=public_question_compare_key($text);
        if($key===''||isset($seen[$key])) continue;
        $seen[$key]=true;
        $id=(int)($row['id']??0);if($id===0)$id=$synthetic--;
        $out[]=['id'=>$id,'answer_text'=>$text];
    }
    return $out;
}

/** @param array<int,array{id?:int,answer_text?:string}> $options */
function murshid_v48_resolve_option_answer(string $raw,array $options): string {
    $raw=trim($raw);if($raw===''||!$options)return $raw;
    $rawKey=public_question_compare_key($raw);
    foreach($options as $o){
        $text=trim((string)($o['answer_text']??''));
        if($text!==''&&public_question_compare_key($text)===$rawKey)return $text;
    }
    if(ctype_digit(ltrim($raw,'-'))){
        foreach($options as $o){if((string)(int)($o['id']??0)===(string)(int)$raw)return (string)$o['answer_text'];}
    }
    $k=mb_strtolower(arabic_grade_normalize($raw),'UTF-8');
    $map=['a'=>0,'أ'=>0,'ا'=>0,'option a'=>0,'option_a'=>0,'b'=>1,'ب'=>1,'option b'=>1,'option_b'=>1,'c'=>2,'ج'=>2,'option c'=>2,'option_c'=>2,'d'=>3,'د'=>3,'option d'=>3,'option_d'=>3,'e'=>4,'هـ'=>4,'ه'=>4];
    if(isset($map[$k],$options[$map[$k]]))return (string)$options[$map[$k]]['answer_text'];
    if(in_array($k,['1','2','3','4','5'],true)){
        $same=false;foreach($options as $o)if(public_question_compare_key((string)$o['answer_text'])===$k){$same=true;break;}
        $i=(int)$k-1;if(!$same&&isset($options[$i]))return (string)$options[$i]['answer_text'];
    }
    return $raw;
}

function murshid_v48_normalize_true_false_answer(string $raw): string {
    $n=arabic_grade_normalize($raw);
    if(in_array($n,['true','1','yes','صح','صحيح','صحيحه','صواب'],true))return 'صح';
    if(in_array($n,['false','0','no','خطا','خطاء','غير صحيح','غيرصحيح'],true))return 'خطأ';
    return trim($raw);
}

function murshid_v48_valid_stage_json($value,string $key='stages'): bool {
    $data=is_array($value)?$value:json_decode((string)$value,true);
    return is_array($data)&&is_array($data[$key]??null)&&count($data[$key])>0;
}

/**
 * @param array<string,mixed> $row
 * @param array<int,array{id?:int,answer_text?:string,text?:string}> $options
 * @param array<int,array<string,mixed>> $matchItems
 * @return array{valid:bool,reason:string,row:array<string,mixed>,options:array<int,array{id:int,answer_text:string}>,match_items:array<int,array<string,mixed>>,answer_mode:string}
 */
function murshid_v48_canonical_question(array $row,array $options=[],array $matchItems=[]): array {
    $row['type']=murshid_v48_normalize_type((string)($row['type']??'text'));
    $row['question_text']=trim((string)($row['question_text']??''));
    $row['correct_answer']=trim((string)($row['correct_answer']??''));
    $display=public_question_display_text($row['question_text']);
    $options=murshid_v48_unique_options($options);
    $matchItems=array_values(array_filter($matchItems,static fn($x)=>is_array($x)));

    if($display===''||$row['correct_answer']==='') return ['valid'=>false,'reason'=>'missing_prompt_or_answer','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
    if(function_exists('murshid_v47_is_internal_validation_prompt')&&murshid_v47_is_internal_validation_prompt($row['question_text']))return ['valid'=>false,'reason'=>'internal_validation','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];

    $type=(string)$row['type'];
    $choiceRequired=murshid_v48_choice_required_prompt($display);
    $tfPrompt=murshid_v48_true_false_prompt($display);

    if($tfPrompt&&$type!=='true_false')$type='true_false';

    if($type==='true_false'){
        $row['correct_answer']=murshid_v48_normalize_true_false_answer($row['correct_answer']);
        if(!in_array($row['correct_answer'],['صح','خطأ'],true))return ['valid'=>false,'reason'=>'bad_true_false_answer','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
        $options=[['id'=>-1,'answer_text'=>'صح'],['id'=>-2,'answer_text'=>'خطأ']];
        $row['type']='true_false';
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'choice'];
    }

    if($type==='match'){
        $validItems=[];
        foreach($matchItems as $item){
            if(trim((string)($item['mad_word']??''))===''||trim((string)($item['correct_type']??''))==='')continue;
            $validItems[]=$item;
        }
        if(count($validItems)<1)return ['valid'=>false,'reason'=>'missing_match_items','row'=>$row,'options'=>$options,'match_items'=>$validItems,'answer_mode'=>'invalid'];
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>[],'match_items'=>$validItems,'answer_mode'=>'match'];
    }

    if($type==='calculation'){
        if(!murshid_v48_valid_stage_json($row['calculation_json']??null))return ['valid'=>false,'reason'=>'bad_calculation_definition','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>[],'match_items'=>[],'answer_mode'=>'calculation'];
    }
    if($type==='genetics'){
        if(!murshid_v48_valid_stage_json($row['genetics_json']??null))return ['valid'=>false,'reason'=>'bad_genetics_definition','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>[],'match_items'=>[],'answer_mode'=>'genetics'];
    }

    // السؤال الذي يقول «أي ... مما يأتي؟» لا يجوز عرضه كحقل نصي إذا كانت الخيارات مفقودة.
    if($choiceRequired&&count($options)<2)return ['valid'=>false,'reason'=>'choice_prompt_without_options','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];

    if($type==='mcq'||($choiceRequired&&count($options)>=2)||($type==='meaning'&&count($options)>=2)){
        if(count($options)<2)return ['valid'=>false,'reason'=>'mcq_without_options','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
        $resolved=murshid_v48_resolve_option_answer($row['correct_answer'],$options);
        $resolvedKey=public_question_compare_key($resolved);$found=false;
        foreach($options as $o)if(public_question_compare_key((string)$o['answer_text'])===$resolvedKey){$found=true;break;}
        if(!$found)return ['valid'=>false,'reason'=>'mcq_answer_not_in_options','row'=>$row,'options'=>$options,'match_items'=>$matchItems,'answer_mode'=>'invalid'];
        $row['correct_answer']=$resolved;
        if($choiceRequired||$type==='mcq')$row['type']='mcq';
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>$options,'match_items'=>[],'answer_mode'=>'choice'];
    }

    // وجود خيارات حقيقية لسؤال نصي مع صياغة غير اختيارية لا يغير نوعه تلقائياً؛
    // لكن لا نرسل الخيارات حتى لا يتعارض مسار الواجهة مع مسار التصحيح.
    if(in_array($type,['fill','meaning','text'],true)){
        return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>[],'match_items'=>[],'answer_mode'=>$type==='fill'?'fill':'text'];
    }

    // الأنواع غير المعروفة تتحول إلى نص فقط إذا كانت قابلة للتصحيح نصياً.
    $row['type']='text';
    return ['valid'=>true,'reason'=>'','row'=>$row,'options'=>[],'match_items'=>[],'answer_mode'=>'text'];
}

/**
 * يحمّل العلاقات (answers/match_items) مرة واحدة ثم يعيد الأسئلة الصالحة فقط.
 * كل مسارات الطالب تستخدم هذا الناتج نفسه حتى لا يختلف العرض عن التصحيح.
 * @param array<int,array<string,mixed>> $rows
 * @return array<int,array<string,mixed>>
 */
function murshid_v48_prepare_rows(PDO $pdo,array $rows,bool $dedupe=true): array {
    $ids=[];foreach($rows as $r){$id=(int)($r['id']??0);if($id>0)$ids[]=$id;}
    $answers=[];$matches=[];
    if($ids){
        $ids=array_values(array_unique($ids));$marks=implode(',',array_fill(0,count($ids),'?'));
        try{$q=$pdo->prepare("SELECT question_id,id,answer_text FROM answers WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");$q->execute($ids);foreach($q->fetchAll(PDO::FETCH_ASSOC)?:[] as $a)$answers[(int)$a['question_id']][]=$a;}catch(Throwable $e){error_log('v48 answers: '.$e->getMessage());}
        try{$q=$pdo->prepare("SELECT question_id,id,mad_word,correct_type,reason_text FROM match_items WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");$q->execute($ids);foreach($q->fetchAll(PDO::FETCH_ASSOC)?:[] as $m)$matches[(int)$m['question_id']][]=$m;}catch(Throwable $e){error_log('v48 match: '.$e->getMessage());}
    }
    $out=[];$slot=[];
    foreach($rows as $r){
        $id=(int)($r['id']??0);$c=murshid_v48_canonical_question($r,$answers[$id]??[],$matches[$id]??[]);
        if(!$c['valid'])continue;
        $row=$c['row'];$row['_answer_mode']=$c['answer_mode'];$row['_answer_rows']=$c['options'];$row['_match_items']=$c['match_items'];
        $key=public_question_compare_key(public_question_display_text((string)$row['question_text']))."\0".public_question_compare_key((string)$row['correct_answer']);
        if(!$dedupe||$key===''){$out[]=$row;continue;}
        $score=(count($c['options'])>=2?80:0)+(!empty($c['match_items'])?100:0)+(public_question_explanation($row['explanation']??null,$row['correct_answer']??null)!==''?35:0);
        if(!isset($slot[$key])){$slot[$key]=count($out);$row['_integrity_score']=$score;$out[]=$row;}
        else{$i=$slot[$key];if($score>(int)($out[$i]['_integrity_score']??0)){$row['_integrity_score']=$score;$out[$i]=$row;}}
    }
    foreach($out as &$r)unset($r['_integrity_score']);unset($r);
    return array_values($out);
}

function murshid_apply_question_integrity_v48_once(): void {
    $marker=__DIR__.'/../storage/.question_integrity_v48_done';
    if(is_file($marker))return;
    try{$pdo=db();}catch(Throwable $e){return;}
    $lock='murshid_question_integrity_v48';$locked=false;
    try{
        $pdo->exec("CREATE TABLE IF NOT EXISTS content_migrations (code VARCHAR(120) PRIMARY KEY,details VARCHAR(255) NULL,completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $code='question_integrity_v48_20261007';$q=$pdo->prepare('SELECT 1 FROM content_migrations WHERE code=? LIMIT 1');$q->execute([$code]);
        if($q->fetchColumn()){@file_put_contents($marker,'done');return;}
        try{$q=$pdo->prepare('SELECT GET_LOCK(?,0)');$q->execute([$lock]);$locked=((int)$q->fetchColumn()===1);}catch(Throwable $e){$locked=true;}
        if(!$locked)return;

        $rows=$pdo->query("SELECT q.id,q.type,q.question_text,q.correct_answer,q.explanation,q.genetics_json,q.calculation_json,q.status FROM questions q ORDER BY q.id")->fetchAll(PDO::FETCH_ASSOC)?:[];
        $ids=array_map(static fn($r)=>(int)$r['id'],$rows);$answers=[];$matches=[];
        foreach(array_chunk(array_values(array_filter($ids)),800) as $chunk){
            $marks=implode(',',array_fill(0,count($chunk),'?'));
            try{$a=$pdo->prepare("SELECT question_id,id,answer_text FROM answers WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");$a->execute($chunk);foreach($a->fetchAll(PDO::FETCH_ASSOC)?:[] as $x)$answers[(int)$x['question_id']][]=$x;}catch(Throwable $e){error_log('v48 migration answers: '.$e->getMessage());}
            try{$m=$pdo->prepare("SELECT question_id,id,mad_word,correct_type,reason_text FROM match_items WHERE question_id IN ($marks) ORDER BY question_id,sort_order,id");$m->execute($chunk);foreach($m->fetchAll(PDO::FETCH_ASSOC)?:[] as $x)$matches[(int)$x['question_id']][]=$x;}catch(Throwable $e){error_log('v48 migration match: '.$e->getMessage());}
        }
        $disable=$pdo->prepare('UPDATE questions SET status=0 WHERE id=?');
        $update=$pdo->prepare('UPDATE questions SET type=?,correct_answer=? WHERE id=?');
        $hasAns=$pdo->prepare('SELECT COUNT(*) FROM answers WHERE question_id=? AND answer_text=?');
        $insAns=$pdo->prepare('INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)');
        $disabled=0;$fixedType=0;$fixedAnswer=0;$tfAdded=0;
        foreach($rows as $r){
            if((int)($r['status']??1)!==1)continue;$id=(int)$r['id'];
            $c=murshid_v48_canonical_question($r,$answers[$id]??[],$matches[$id]??[]);
            if(!$c['valid']){$disable->execute([$id]);$disabled++;continue;}
            $nr=$c['row'];$oldType=murshid_v48_normalize_type((string)($r['type']??''));$newType=(string)$nr['type'];$oldAns=trim((string)($r['correct_answer']??''));$newAns=trim((string)$nr['correct_answer']);
            if($oldType!==$newType)$fixedType++;if($oldAns!==$newAns)$fixedAnswer++;
            if($oldType!==$newType||$oldAns!==$newAns)$update->execute([$newType,$newAns,$id]);
            if($newType==='true_false'){
                foreach([['صح',1],['خطأ',2]] as [$label,$sort]){$hasAns->execute([$id,$label]);if(!(int)$hasAns->fetchColumn()){$insAns->execute([$id,$label,$sort]);$tfAdded++;}}
            }
        }
        $details="disabled={$disabled}; type={$fixedType}; answer={$fixedAnswer}; tf={$tfAdded}";
        $pdo->prepare("INSERT INTO content_migrations(code,details,completed_at) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE details=VALUES(details),completed_at=NOW()")
            ->execute([$code,substr($details,0,255)]);
        @file_put_contents($marker,'done');
    }catch(Throwable $e){error_log('question integrity v48: '.$e->getMessage());}
    finally{if($locked){try{$q=$pdo->prepare('SELECT RELEASE_LOCK(?)');$q->execute([$lock]);}catch(Throwable $e){}}}
}
