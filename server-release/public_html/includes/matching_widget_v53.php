<?php
declare(strict_types=1);
require_once __DIR__.'/question_presentation_v53.php';
function murshid_v53_matching_widget(array $set,string $name,string $class='',int $questionId=0): void {
    $definition=htmlspecialchars((string)json_encode(murshid_v53_public_matching($set),JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR),ENT_QUOTES,'UTF-8');
    echo '<div data-v53-matching data-definition="'.$definition.'"><input type="hidden" name="'.htmlspecialchars($name,ENT_QUOTES,'UTF-8').'" class="'.htmlspecialchars($class,ENT_QUOTES,'UTF-8').'" data-q="'.$questionId.'" value=""></div>';
}
