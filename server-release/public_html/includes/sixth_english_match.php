<?php
declare(strict_types=1);

function sixth_english_match_normalize_question(string $text): string {
    $text=str_replace(["\r\n","\r"],"\n",trim($text));
    $text=preg_replace('/[ \t]+/u',' ',$text) ?? $text;
    $text=preg_replace('/\n+/u','\n',$text) ?? $text;
    return trim($text);
}

function sixth_english_match_sets(): array {
    static $map=null;
    if(is_array($map)) return $map;
    $map=[];
    $path=__DIR__.'/data/sixth_english_match_sets.json';
    $raw=@file_get_contents($path);
    $rows=$raw!==false?json_decode($raw,true):null;
    if(!is_array($rows)) return $map;
    foreach($rows as $row){
        if(!is_array($row)||empty($row['question'])) continue;
        $key=hash('sha256',sixth_english_match_normalize_question((string)$row['question']));
        $map[$key]=$row;
    }
    return $map;
}

function sixth_english_match_for_question(string $question): ?array {
    $key=hash('sha256',sixth_english_match_normalize_question($question));
    $all=sixth_english_match_sets();
    return isset($all[$key]) && is_array($all[$key]) ? $all[$key] : null;
}
