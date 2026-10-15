<?php
declare(strict_types=1);
require_once __DIR__.'/sixth_english_match.php';

/** Generated validation wrappers are not educational questions in their own right. */
function murshid_v53_generated_prompt(string $text): bool {
    foreach (['الإجابة المناسبة للسؤال','— هل الإجابة','الإجابة عن «','عند الإجابة عن «',
      'راجع المعلومة: يرتبط السؤال','تدريب تثبيت: الجواب','وفق المفهوم الدراسي، هل إجابة',
      'قيّم العبارة: إذا سُئلت','اختر صح أو خطأ للرابطة','تأكد من المراجعة: يقابل السؤال'] as $marker) {
        if (str_contains($text,$marker)) return true;
    }
    return false;
}

function murshid_v53_matching_definition(string $text): ?array {
    if (murshid_v53_generated_prompt($text)) return null;
    $text=preg_replace('/^Wazari recall\s+\d+\s*:\s*/iu','',trim($text))??$text;
    $set=sixth_english_match_for_question($text);
    if (!$set) return null;
    $left=$set['left']??[]; $right=$set['right']??[]; $map=$set['map']??[];
    if (!is_array($left)||!is_array($right)||!is_array($map)||count($left)<2||count($left)!==count($map)) return null;
    $used=[];
    foreach($left as $key=>$label) {
        $target=(string)($map[$key]??'');
        if(trim((string)$label)===''||$target===''||!isset($right[$target])||trim((string)$right[$target])===''||isset($used[$target])) return null;
        $used[$target]=true;
    }
    return ['left'=>$left,'right'=>$right,'map'=>$map];
}

/** Public payload intentionally contains no solution map. */
function murshid_v53_public_matching(?array $set): ?array {
    return $set ? ['left'=>(object)$set['left'],'right'=>(object)$set['right']] : null;
}

function murshid_v53_parse_pairs(string $answer): ?array {
    $answer=strtr(trim($answer),['٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9','،'=>',','−'=>'-','–'=>'-']);
    if(!preg_match('/^\d+\s*-\s*[a-z](?:\s*[,;]\s*\d+\s*-\s*[a-z])*$/iD',$answer))return null;
    preg_match_all('/(\d+)\s*-\s*([a-z])/i',$answer,$matches,PREG_SET_ORDER);
    $pairs=[];$used=[];
    foreach($matches as $m){$key=(string)(int)$m[1];$target=strtolower($m[2]);if(isset($pairs[$key])||isset($used[$target]))return null;$pairs[$key]=$target;$used[$target]=true;}
    ksort($pairs,SORT_NATURAL);return $pairs;
}

function murshid_v53_pair_grade(string $given,string $correct): ?array {
    $expected=murshid_v53_parse_pairs($correct);
    if(!$expected||count($expected)<2)return null;
    $actual=murshid_v53_parse_pairs($given);$ok=$actual!==null&&$actual===$expected;
    return ['is_correct'=>$ok,'status'=>$ok?'correct':'incorrect','score'=>$ok?100:0,
      'message'=>$ok?'جميع التوصيلات صحيحة':'توجد توصيلات ناقصة أو غير صحيحة'];
}
