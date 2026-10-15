<?php
declare(strict_types=1);
require_once __DIR__.'/../public_html/includes/hint_free.php';
function expect_same(mixed $got,mixed $expected): void {
    if($got!==$expected)throw new RuntimeException('Question presentation mismatch: '.json_encode($got,JSON_UNESCAPED_UNICODE));
}
expect_same(murshid_without_inline_hints("احسب 2 + 3.\nتلميح: الناتج خمسة"),'احسب 2 + 3.');
expect_same(murshid_without_inline_hints("Find x: x² = 4.\nHint: x = 2"),'Find x: x² = 4.');
expect_same(murshid_without_inline_hints('ما معنى كلمة تلميح؟'),'ما معنى كلمة تلميح؟');
$definition=['stages'=>[['key'=>'x','kind'=>'number','correct'=>'4','unit'=>'cm²','hint'=>'5','wrong_hint'=>'5','hintText'=>'5']]];
expect_same(murshid_strip_hints($definition),['stages'=>[['key'=>'x','kind'=>'number','correct'=>'4','unit'=>'cm²']]]);
foreach(['P ≠ p؛ 10²؛ عِلْم','﴿قُلْ هُوَ اللَّهُ أَحَدٌ﴾','قال رسول الله ﷺ'] as $text)
    expect_same(murshid_without_inline_hints($text),$text);
echo "PASS actual PHP nested hint protection and notation preservation\n";
