<?php
declare(strict_types=1);
require_once __DIR__.'/../public_html/includes/functions.php';
require_once __DIR__.'/../public_html/includes/question_presentation_v53.php';
function matching_check(bool $ok,string $label): void {if(!$ok)throw new RuntimeException($label);}
$cases=0;
foreach(sixth_english_match_sets() as $set){
    $definition=murshid_v53_matching_definition($set['question']);
    matching_check($definition!==null,'Missing structured source');
    $public=murshid_v53_public_matching($definition);
    matching_check(!isset($public['map']),'Solution map leaked');
    $correct=$set['answer'];$pairs=explode(',',$correct);
    matching_check(murshid_v53_pair_grade(implode(',',array_reverse($pairs)),$correct)['is_correct'],'Order-sensitive grading');
    matching_check(is_arabic_answer_correct($correct,$correct,'text',$set['question']),'Real grading integration');
    matching_check(!is_arabic_answer_correct($pairs[0],$correct,'text',$set['question']),'Partial answer accepted');
    matching_check(!murshid_v53_pair_grade($correct.', '.$pairs[0],$correct)['is_correct'],'Duplicate key accepted');
    matching_check(!murshid_v53_pair_grade($correct.' junk',$correct)['is_correct'],'Trailing junk accepted');
    $wrapper='هل العبارة التالية صحيحة أم خاطئة؟ الإجابة المناسبة للسؤال «'.$set['question'].'» هي «'.$correct.'».';
    matching_check(murshid_v53_matching_definition($wrapper)===null,'Generated wrapper became a matching question');
    $cases+=8;
}
matching_check(!murshid_v53_generated_prompt('اسم المفعول يدل على من وقع عليه الفعل.'),'Original true/false rejected');
matching_check(murshid_v53_parse_pairs('١-c، ٢-a')===[1=>'c',2=>'a'],'Arabic digits rejected');
echo 'PASS '.($cases+2)." matching source and real grading assertions\n";
