<?php
declare(strict_types=1);

/** Display-only filter. Does not rewrite answers, units, vowels or mathematics. */
function murshid_without_inline_hints(string $text): string {
    $text=str_replace("\r\n", "\n", $text);
    $text=preg_replace('/^[ \t]*(?:تلميح|تلميحات|hint|hints|clue|tip)[ \t]*[:：][^\n]*(?:\n|$)/imu', '', $text) ?? $text;
    $text=preg_replace('/[ \t]*[（(](?:تلميح|تلميحات|hint|hints|clue|tip)[ \t]*[:：][^）)\n]*[）)][ \t]*$/iu', '', $text) ?? $text;
    return trim($text);
}

/** Old and newly authored nested problem payloads share the same hint policy. */
function murshid_strip_hints(mixed $value): mixed {
    if(!is_array($value)) return $value;
    foreach($value as $key=>$item) {
        $name=is_string($key) ? preg_replace('/([a-z])([A-Z])/', '$1_$2', $key) : '';
        if(is_string($name) && preg_match('/(^|[_-])(?:hints?|clues?|tips?)($|[_-])/i', $name)) {
            unset($value[$key]);
        } else {
            $value[$key]=murshid_strip_hints($item);
        }
    }
    return $value;
}
