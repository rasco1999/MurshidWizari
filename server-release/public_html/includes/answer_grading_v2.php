<?php
/**
 * Murshid answer grading v2: conservative Quran transcription + safe math evaluation.
 * No database changes or remote dependencies. Requires PHP 8.1 and mbstring.
 */
declare(strict_types=1);

function murshid_quran_transcription_prompt(string $question): bool {
    // Only verbatim memorisation/completion questions. Explanation/meaning questions stay flexible.
    return (bool)preg_match('/(?:اكتب|أكتب|اكْتب|اكمل|أكمل|اتم|أتم|اتل|ارتل|رتل|استظهر|اذكر|أذكر)\b.{0,110}?(?:سورة|الآية|الاية|آية|اية|قوله\s+تعالى|من\s+القرآن|دليل(?:اً|ا|\s+)\s*قرآني)/u', $question);
}
function murshid_quran_clean(string $s): string {
    $s=html_entity_decode($s, ENT_QUOTES|ENT_HTML5,'UTF-8');
    $s=preg_replace('/^\s*(?:قال\s+(?:الله\s+)?تعالى|قوله\s+تعالى)\s*[:：]?\s*/u','',$s)??$s;
    $s=preg_replace('/[\x{0610}-\x{061A}\x{064B}-\x{065F}\x{0670}\x{06D6}-\x{06ED}\x{0640}\x{06DD}\x{06DE}]/u','',$s)??$s;
    $s=strtr($s,['ٱ'=>'ا','ى'=>'ي','۝'=>' ','ﷲ'=>'الله']);
    $s=preg_replace('/[^\p{L}\p{N}]+/u',' ',$s)??$s;
    return trim(preg_replace('/\s+/u',' ',$s)??$s);
}
function murshid_quran_same_word(string $a,string $b): bool {
    if($a===$b)return true;
    // Writing an unhamzated alif is accepted, but conflicting explicit hamzas are not.
    $x=preg_split('//u',$a,-1,PREG_SPLIT_NO_EMPTY);
    $y=preg_split('//u',$b,-1,PREG_SPLIT_NO_EMPTY);
    if(!is_array($x)||!is_array($y)||count($x)!==count($y))return false;
    foreach($x as $i=>$c){
        if($c===$y[$i])continue;
        if(in_array($c,['ا','أ','إ','آ'],true)&&in_array($y[$i],['ا','أ','إ','آ'],true)&&($c==='ا'||$y[$i]==='ا'))continue;
        return false;
    }
    return true;
}
function murshid_quran_same(string $given,string $correct): bool {
    $g=murshid_quran_clean($given);if($g==='')return false;
    foreach(explode('||',$correct) as $model){
        $c=murshid_quran_clean($model);if($c==='')continue;
        $ga=explode(' ',$g);$ca=explode(' ',$c);
        if(count($ga)!==count($ca))continue;
        $match=true;
        foreach($ca as $i=>$word){if(!murshid_quran_same_word($ga[$i],$word)){$match=false;break;}}
        if($match)return true;
    }
    return false;
}
function murshid_math_text(string $raw): string {
    $raw=trim(html_entity_decode($raw,ENT_QUOTES|ENT_HTML5,'UTF-8'));
    $raw=strtr($raw,['٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9','۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9','٫'=>'.','−'=>'-','–'=>'-','×'=>'*','·'=>'*','÷'=>'/','√'=>'sqrt','π'=>'pi','Π'=>'pi','²'=>'^2','³'=>'^3','⁻'=>'-','س'=>'x','ص'=>'y']);
    $raw=str_replace(['−','٠','،'],['-','0',','],$raw);
    $raw=preg_replace('/(?<=\d)[xX](?=10\s*\^)/u','*',$raw)??$raw;
    return mb_strtolower($raw,'UTF-8');
}
function murshid_math_tokens(string $raw): ?array {
    $raw=murshid_math_text($raw);
    if($raw===''||strlen($raw)>280)return null;
    $raw=preg_replace('/\s+/u','',$raw)??$raw;
    $tokens=[];$offset=0;$length=strlen($raw);
    while($offset<$length){
        if(!preg_match('/\G(?:\d+(?:\.\d+)?(?:e[+\-]?\d+)?|\.\d+(?:e[+\-]?\d+)?|[a-z]+|[()+*\/^\-=])/Ai',$raw,$m,0,$offset))return null;
        $t=$m[0];$offset+=strlen($t);$tokens[]=$t;
        if(count($tokens)>120)return null;
    }
    foreach($tokens as $t){if(ctype_alpha($t)&&!in_array($t,['x','y','pi','sqrt'],true))return null;}
    // Implicit multiplication: 2x, 2sqrt(3), (x-3)(x+3), 2pi.
    $out=[];$previous=null;
    foreach($tokens as $token){
        $prevEnd=$previous!==null&&($previous===')'||is_numeric($previous)||in_array($previous,['x','y','pi'],true));
        $nextStart=$token==='('||is_numeric($token)||in_array($token,['x','y','pi','sqrt'],true);
        if($prevEnd&&$nextStart)$out[]='*';
        $out[]=$token;$previous=$token;
    }
    return $out;
}
function murshid_math_eval_tokens(array $t,array $vars=[]): ?float {
    $i=0;$length=count($t);
    $parse=null;
    $parse=static function(int $minBp=0) use(&$parse,&$i,$t,$length,$vars): float {
        if($i>=$length)throw new RuntimeException('Empty expression');
        $v=$t[$i++];
        if($v==='+'||$v==='-'){$left=$parse(6);if($v==='-')$left=-$left;}
        elseif($v==='('){$left=$parse(0);if(($t[$i]??'')!==')')throw new RuntimeException('Missing )');$i++;}
        elseif($v==='sqrt'){
            if(($t[$i]??'')==='('){$i++;$arg=$parse(0);if(($t[$i]??'')!==')')throw new RuntimeException('Missing )');$i++;}
            else $arg=$parse(6);
            if($arg<0)throw new RuntimeException('Negative root');$left=sqrt($arg);
        }
        elseif($v==='pi')$left=M_PI;
        elseif($v==='x'||$v==='y')$left=(float)($vars[$v]??0);
        elseif(is_numeric($v))$left=(float)$v;
        else throw new RuntimeException('Unexpected token');
        while($i<$length){
            $op=$t[$i];
            $binding=match($op){'+','-'=>[1,2],'*','/'=>[3,4],'^'=>[7,7],default=>null};
            if($binding===null||$binding[0]<$minBp)break;
            $i++;$right=$parse($binding[1]);
            if($op==='+')$left+=$right;
            elseif($op==='-')$left-=$right;
            elseif($op==='*')$left*=$right;
            elseif($op==='/'){
                if(abs($right)<1.e-15)throw new RuntimeException('Division by zero');$left/=$right;
            }elseif($op==='^'){
                if(abs($right)>40||abs($left)>1e100)throw new RuntimeException('Exponent too large');
                $left=pow($left,$right);
            }
            if(!is_finite($left)||abs($left)>1e100)throw new RuntimeException('Nonfinite math');
        }
        return $left;
    };
    try{$n=$parse(0);return $i===$length&&is_finite($n)?$n:null;}
    catch(Throwable $e){return null;}
}
function murshid_math_value(string $expr,array $vars=[]): ?float {
    $tokens=murshid_math_tokens($expr);
    if($tokens===null||in_array('=',$tokens,true))return null;
    return murshid_math_eval_tokens($tokens,$vars);
}
function murshid_math_close(float $a,float $b, float $tol=0.00001): bool {
    return abs($a-$b)<=max(1e-9,$tol*max(1.0,abs($a),abs($b)));
}
/** null: not a supported pure mathematical model; otherwise exact/symbolic comparison. */
function murshid_math_equivalent(string $given,string $model): ?bool {
    $c=murshid_math_tokens($model);if($c===null)return null;
    $g=murshid_math_tokens($given);if($g===null)return false;
    $eqCount=count(array_filter($c,static fn($v)=>$v==='='));
    if($eqCount>1||count(array_filter($g,static fn($v)=>$v==='='))>1)return false;
    $format=static function(array $t): array {
        $sep=array_search('=',$t,true);
        if($sep===false)return [$t,null];
        return [array_slice($t,0,$sep),array_slice($t,$sep+1)];
    };
    [$cl,$cr]=$format($c);[$gl,$gr]=$format($g);
    if(!$cl||!$gl||($cr===[]||$gr===[]))return false;
    // A bare value may answer x=5, but not a general equation with multiple roots.
    if($cr!==null&&$gr===null&&count($cl)===1&&in_array($cl[0],['x','y'],true)&&!in_array('x',$g,true)&&!in_array('y',$g,true)){$gl=$g;$gr=null;$cl=$cr;$cr=null;}
    if($cr===null&&$gr!==null&&count($gl)===1&&in_array($gl[0],['x','y'],true)&&!in_array('x',$c,true)&&!in_array('y',$c,true)){$gl=$gr;$gr=null;}
    if(($cr===null)!==($gr===null))return false;
    $hasVar=in_array('x',array_merge($c,$g),true)||in_array('y',array_merge($c,$g),true);
    $points=$hasVar?[[0,1],[1,2],[-2,3],[3,-1],[5,4],[7,-3],[-4,-2]]:[[0,0]];
    $ratio=null;$valid=0;
    foreach($points as [$x,$y]){
        $vars=['x'=>$x,'y'=>$y];
        $c1=murshid_math_eval_tokens($cl,$vars);$g1=murshid_math_eval_tokens($gl,$vars);
        $c2=$cr!==null?murshid_math_eval_tokens($cr,$vars):0.0;
        $g2=$gr!==null?murshid_math_eval_tokens($gr,$vars):0.0;
        if($c1===null||$g1===null||$c2===null||$g2===null)continue;
        $a=$c1-$c2;$b=$g1-$g2;$valid++;
        if($cr===null){if(!murshid_math_close($a,$b))return false;continue;}
        // Equations may be multiplied by a nonzero constant or have sides exchanged.
        if(abs($a)<1e-7||abs($b)<1e-7){if(!murshid_math_close($a,$b))return false;continue;}
        $r=$b/$a;
        if($ratio===null)$ratio=$r;
        elseif(!murshid_math_close($r,$ratio))return false;
    }
    return $valid>=($hasVar?3:1);
}
function murshid_is_math_question(string $model,string $question,string $type): bool {
    if(in_array($type,['mcq','true_false','match','meaning','genetics'],true))return false;
    if(murshid_math_tokens($model)===null)return false;
    if((bool)preg_match('/[=+*\/^√π²³]/u',$model))return true;
    return (bool)preg_match('/(?:احسب|أحسب|اوجد|أوجد|قيمة|حل\s+(?:المعادلة|المسألة)|المعادلة|الناتج|العدد\s+المطلوب|يساوي|كم\s+(?:يساوي|تبلغ|يبلغ)|حوّل|حول|احسب|قِس)/u',$question);
}
/** Validates a whole numeric answer, optional physical unit (no more substring matching). */
function murshid_math_number_with_unit(string $given,string $expectedUnit=''): ?float {
    $expr=trim($given);
    $value=murshid_math_value($expr);
    if($value!==null)return $value;
    if($expectedUnit==='')return null;
    $groups=[
        'F'=>['F'=>1.0,'mF'=>1e-3,'μF'=>1e-6,'µF'=>1e-6,'uF'=>1e-6,'nF'=>1e-9,'pF'=>1e-12],
        'C'=>['C'=>1.0,'mC'=>1e-3,'μC'=>1e-6,'µC'=>1e-6,'uC'=>1e-6],
        'V'=>['V'=>1.0,'mV'=>1e-3,'kV'=>1e3],
        'A'=>['A'=>1.0,'mA'=>1e-3,'μA'=>1e-6],
        'Ω'=>['Ω'=>1.0,'kΩ'=>1e3,'MΩ'=>1e6,'ohm'=>1.0],
        'm'=>['m'=>1.0,'cm'=>0.01,'mm'=>0.001,'km'=>1000.0],
        'm²'=>['m²'=>1.0,'cm²'=>0.0001,'mm²'=>1.e-6],
        'J'=>['J'=>1.0,'kJ'=>1000.0],
        'W'=>['W'=>1.0,'kW'=>1000.0],
        'Hz'=>['Hz'=>1.0,'kHz'=>1000.0],
    ];
    $supported=null;
    foreach($groups as $group){if(array_key_exists($expectedUnit,$group)){$supported=$group;break;}}
    $units=$supported??[$expectedUnit=>1.0];
    uksort($units,static fn($x,$y)=>strlen($y)<=>strlen($x));
    foreach($units as $unit=>$factor){
        if(!str_ends_with($expr,$unit))continue;
        $without=trim(substr($expr,0,-strlen($unit)));
        if($without==='')continue;
        $number=murshid_math_value($without);
        if($number!==null)return $number*$factor/($units[$expectedUnit]??1.0);
    }
    return null;
}
