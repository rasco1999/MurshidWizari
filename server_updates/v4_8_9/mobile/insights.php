<?php
declare(strict_types=1);

// Backwards-compatible weekly accuracy comparison based on recorded answers.
require_once __DIR__ . '/common.php';
require_once __DIR__ . '/../includes/learning_catalog.php';

$student = mobile_student();
$userId = (int) $student['id'];
$gradeId = (int) $student['grade_id'];
$pdo = db();
$insights = student_learning_insights($pdo, $userId, $gradeId);

$query = $pdo->prepare(
    'SELECT COUNT(*) AS answers, COALESCE(SUM(is_correct),0) AS correct
       FROM answer_events
      WHERE user_id=? AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)'
);
$query->execute([$userId]);
$current = $query->fetch(PDO::FETCH_ASSOC) ?: [];

$query = $pdo->prepare(
    'SELECT COUNT(*) AS answers, COALESCE(SUM(is_correct),0) AS correct
       FROM answer_events
      WHERE user_id=? AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
        AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)'
);
$query->execute([$userId]);
$previous = $query->fetch(PDO::FETCH_ASSOC) ?: [];

$currentAnswers = (int) ($current['answers'] ?? 0);
$currentCorrect = (int) ($current['correct'] ?? 0);
$previousAnswers = (int) ($previous['answers'] ?? 0);
$previousCorrect = (int) ($previous['correct'] ?? 0);
$accuracy = $currentAnswers ? (int) round(100 * $currentCorrect / $currentAnswers) : 0;
$previousAccuracy = $previousAnswers ? (int) round(100 * $previousCorrect / $previousAnswers) : 0;

json_response([
    'ok' => true,
    'insights' => $insights,
    'weekly' => [
        'answers' => $currentAnswers,
        'correct' => $currentCorrect,
        'accuracy' => $accuracy,
        'previous' => $previousAnswers,
        'previous_correct' => $previousCorrect,
        'previous_accuracy' => $previousAccuracy,
        'accuracy_change' => $currentAnswers > 0 && $previousAnswers > 0
            ? $accuracy - $previousAccuracy : null,
        'change' => $currentAnswers - $previousAnswers,
    ],
]);
