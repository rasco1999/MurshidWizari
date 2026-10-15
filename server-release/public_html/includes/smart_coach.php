<?php
declare(strict_types=1);

/**
 * المدرب الذكي: مراجعة متباعدة (Leitner) + تحليل نقاط الضعف + مؤشر الجاهزية.
 * لا يغيّر answer_events ولا حصة الأسئلة المجانية؛ يعمل بجدول مستقل review_schedule.
 */
const COACH_INTERVAL_DAYS = [0 => 0, 1 => 1, 2 => 3, 3 => 7, 4 => 14, 5 => 30];
const COACH_TYPES = ['mcq', 'true_false', 'text'];

function coach_ensure_schema(PDO $pdo): void {
    static $done = false;
    if ($done) return;
    $pdo->exec("CREATE TABLE IF NOT EXISTS review_schedule (
        user_id INT UNSIGNED NOT NULL,
        question_id INT UNSIGNED NOT NULL,
        chapter_id INT UNSIGNED NOT NULL,
        box TINYINT UNSIGNED NOT NULL DEFAULT 0,
        due_at DATETIME NOT NULL,
        reps INT UNSIGNED NOT NULL DEFAULT 0,
        lapses INT UNSIGNED NOT NULL DEFAULT 0,
        last_at DATETIME NULL,
        PRIMARY KEY(user_id,question_id),
        INDEX idx_rs_due(user_id,due_at),
        INDEX idx_rs_chapter(user_id,chapter_id),
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ai_usage (
        user_id INT UNSIGNED NOT NULL,
        day DATE NOT NULL,
        used INT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY(user_id,day),
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $done = true;
}

/** تُستدعى عند تسجيل أول إجابة على سؤال. */
function coach_on_answer(PDO $pdo, int $uid, int $qid, int $chapterId, bool $ok): void {
    coach_ensure_schema($pdo);
    $box = $ok ? 1 : 0;
    $days = COACH_INTERVAL_DAYS[$box];
    $pdo->prepare("INSERT IGNORE INTO review_schedule(user_id,question_id,chapter_id,box,due_at,reps,lapses,last_at)
        VALUES(?,?,?,?,DATE_ADD(NOW(),INTERVAL $days DAY),1,?,NOW())")
        ->execute([$uid, $qid, $chapterId, $box, $ok ? 0 : 1]);
}

/** يستورد الإجابات القديمة مرة واحدة لكل طالب (الصحيحة بعد 3 أيام من إجابتها، والخاطئة مستحقة الآن). */
function coach_backfill(PDO $pdo, int $uid): void {
    coach_ensure_schema($pdo);
    $pdo->prepare("INSERT IGNORE INTO review_schedule(user_id,question_id,chapter_id,box,due_at,reps,lapses,last_at)
        SELECT ae.user_id,ae.question_id,ae.chapter_id,IF(ae.is_correct=1,2,0),
               IF(ae.is_correct=1,DATE_ADD(ae.created_at,INTERVAL 3 DAY),NOW()),1,IF(ae.is_correct=1,0,1),ae.created_at
        FROM answer_events ae WHERE ae.user_id=?")->execute([$uid]);
}

function coach_stats(PDO $pdo, int $uid, int $gradeId): array {
    coach_backfill($pdo, $uid);
    $live = topic_live_sql('q');
    $types = "'" . implode("','", COACH_TYPES) . "'";

    $due = $pdo->prepare("SELECT COUNT(*) FROM review_schedule r JOIN questions q ON q.id=r.question_id
        JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
        WHERE r.user_id=? AND r.due_at<=NOW() AND s.grade_id=? AND c.status=1 AND s.status=1 AND $live AND q.type IN($types)");
    $due->execute([$uid, $gradeId]);

    $ch = $pdo->prepare("SELECT c.id chapter_id,c.name chapter_name,s.name subject_name,
            COUNT(*) total,SUM(a.is_correct) correct
        FROM answer_events a JOIN chapters c ON c.id=a.chapter_id JOIN subjects s ON s.id=c.subject_id
        WHERE a.user_id=? AND s.grade_id=? GROUP BY c.id,c.name,s.name");
    $ch->execute([$uid, $gradeId]);
    $rows = $ch->fetchAll();

    $sumC = 0; $sumT = 0; $weak = [];
    foreach ($rows as $r) {
        $t = (int)$r['total']; $c = (int)$r['correct'];
        $sumC += $c; $sumT += $t;
        // تنعيم لابلاس: لا نحكم على موضوع بسؤالين فقط حكماً قاطعاً
        $r['score'] = round(100 * ($c + 1) / ($t + 2));
        $r['wrong'] = $t - $c;
        $weak[] = $r;
    }
    usort($weak, fn($a, $b) => $a['score'] <=> $b['score'] ?: $b['wrong'] <=> $a['wrong']);

    $mastered = $pdo->prepare("SELECT COUNT(*) FROM review_schedule WHERE user_id=? AND box>=4");
    $mastered->execute([$uid]);

    // الجاهزية = دقة منعّمة × عامل تغطية (كلما جرّب الطالب أسئلة أكثر زادت الثقة، حتى 150 سؤالاً)
    $accuracy = $sumT ? ($sumC + 1) / ($sumT + 2) : 0;
    $coverage = min(1, $sumT / 150);
    $readiness = (int)round(100 * $accuracy * (0.4 + 0.6 * $coverage));

    $st = $pdo->prepare("SELECT streak FROM user_stats WHERE user_id=?");
    $st->execute([$uid]);

    return [
        'due' => (int)$due->fetchColumn(),
        'answered' => $sumT,
        'accuracy' => $sumT ? (int)round(100 * $sumC / $sumT) : 0,
        'readiness' => $sumT ? $readiness : 0,
        'mastered' => (int)$mastered->fetchColumn(),
        'streak' => (int)($st->fetchColumn() ?: 0),
        'weak' => array_slice($weak, 0, 5),
    ];
}

/** دفعة أسئلة مستحقة. لا نرسل الإجابة الصحيحة للمتصفح. */
function coach_next_batch(PDO $pdo, int $uid, int $gradeId, int $limit = 10, int $chapterId = 0): array {
    coach_backfill($pdo, $uid);
    $live = topic_live_sql('q');
    $types = "'" . implode("','", COACH_TYPES) . "'";
    $extra = $chapterId > 0 ? ' AND r.chapter_id=' . $chapterId : '';
    $st = $pdo->prepare("SELECT q.id,q.type,q.question_text,c.name chapter_name,s.name subject_name
        FROM review_schedule r JOIN questions q ON q.id=r.question_id
        JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
        WHERE r.user_id=? AND r.due_at<=NOW() AND s.grade_id=? AND c.status=1 AND s.status=1 AND $live AND q.type IN($types) $extra
        ORDER BY r.box ASC,r.due_at ASC LIMIT " . max(1, min(30, $limit)));
    $st->execute([$uid, $gradeId]);
    $items = $st->fetchAll();
    foreach ($items as &$it) {
        $it['id'] = (int)$it['id'];
        $it['options'] = [];
        if ($it['type'] === 'mcq') {
            $o = $pdo->prepare("SELECT answer_text FROM answers WHERE question_id=? ORDER BY sort_order,id");
            $o->execute([$it['id']]);
            $it['options'] = $o->fetchAll(PDO::FETCH_COLUMN);
        } elseif ($it['type'] === 'true_false') {
            $it['options'] = ['صح', 'خطأ'];
        }
        $it['subject_name'] = (string)$it['subject_name'];
        $it['chapter_name'] = public_topic_name((string)$it['subject_name'], (string)$it['chapter_name']);
    }
    return $items;
}

function coach_grade(PDO $pdo, int $uid, int $gradeId, int $qid, string $given): ?array {
    $live = topic_live_sql('q');
    $st = $pdo->prepare("SELECT q.id,q.chapter_id,q.type,q.question_text,q.correct_answer,q.explanation FROM questions q
        JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
        WHERE q.id=? AND s.grade_id=? AND c.status=1 AND s.status=1 AND $live");
    $st->execute([$qid, $gradeId]);
    $q = $st->fetch();
    if (!$q || !in_array($q['type'], COACH_TYPES, true)) return null;

    $g = grade_arabic_answer(trim($given), (string)$q['correct_answer'], (string)$q['type'], (string)($q['question_text']??''));
    $ok = (bool)($g['is_correct'] ?? false);

    coach_ensure_schema($pdo);
    $cur = $pdo->prepare("SELECT box FROM review_schedule WHERE user_id=? AND question_id=?");
    $cur->execute([$uid, $qid]);
    $box = (int)($cur->fetchColumn() ?: 0);
    $newBox = $ok ? min(5, $box + 1) : 0;
    // الخطأ يعيد السؤال بعد 10 دقائق في نفس الجلسة، والصح يرفعه صندوقاً
    $due = $ok ? 'DATE_ADD(NOW(),INTERVAL ' . COACH_INTERVAL_DAYS[$newBox] . ' DAY)' : 'DATE_ADD(NOW(),INTERVAL 10 MINUTE)';
    $pdo->prepare("INSERT INTO review_schedule(user_id,question_id,chapter_id,box,due_at,reps,lapses,last_at)
        VALUES(?,?,?,?,$due,1,?,NOW())
        ON DUPLICATE KEY UPDATE box=VALUES(box),due_at=VALUES(due_at),reps=reps+1,lapses=lapses+VALUES(lapses),last_at=NOW()")
        ->execute([$uid, $qid, (int)$q['chapter_id'], $newBox, $ok ? 0 : 1]);

    return [
        'is_correct' => $ok,
        'correct_answer' => (string)$q['correct_answer'],
        'explanation' => public_question_explanation($q['explanation'] ?? null, $q['correct_answer'] ?? null),
        'box' => $newBox,
        'next_in_days' => $ok ? COACH_INTERVAL_DAYS[$newBox] : 0,
    ];
}
