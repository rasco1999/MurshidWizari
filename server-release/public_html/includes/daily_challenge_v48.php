<?php
/** One daily difficult question per grade, Baghdad calendar day, no user XP or quota impact. */
declare(strict_types=1);
function murshid_daily_v48_schema(PDO $pdo): void {
    $pdo->exec("CREATE TABLE IF NOT EXISTS daily_challenge_v48_attempts (
      user_id INT UNSIGNED NOT NULL, challenge_date DATE NOT NULL, question_id INT UNSIGNED NOT NULL,
      given_answer TEXT NOT NULL, is_correct TINYINT(1) NOT NULL DEFAULT 0,
      answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(user_id,challenge_date), INDEX(question_id,challenge_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
function murshid_daily_v48_question(PDO $pdo,int $gradeId): ?array {
    $today=(new DateTimeImmutable('now', new DateTimeZone('Asia/Baghdad')))->format('Y-m-d');
    $q=$pdo->prepare("SELECT q.id,q.chapter_id,q.question_text,q.correct_answer,q.explanation,q.type,s.name AS subject_name,
      c.name AS chapter_name
      FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
      WHERE s.grade_id=? AND q.status=1 AND c.status=1 AND s.status=1 AND q.type IN ('mcq','text','fill','meaning','true_false')
        AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%'
        AND q.correct_answer IS NOT NULL AND q.correct_answer<>''
      ORDER BY (CHAR_LENGTH(q.question_text)>=110) DESC, CRC32(CONCAT(?,':',q.id)) DESC LIMIT 1");
    $q->execute([$gradeId,$today]);$question=$q->fetch(PDO::FETCH_ASSOC);
    if(!$question)return null;
    $op=$pdo->prepare("SELECT answer_text FROM answers WHERE question_id=? ORDER BY sort_order,id");$op->execute([(int)$question['id']]);
    $question['options']=array_values(array_filter($op->fetchAll(PDO::FETCH_COLUMN),static fn($v)=>trim((string)$v)!==''));
    if($question['type']==='true_false' && !$question['options'])$question['options']=['صح','خطأ'];
    return $question;
}
function murshid_daily_v48_attempt(PDO $pdo,int $userId): ?array {
    $q=$pdo->prepare('SELECT question_id,given_answer,is_correct,answered_at FROM daily_challenge_v48_attempts WHERE user_id=? AND answered_at > DATE_SUB(NOW(), INTERVAL 24 HOUR) ORDER BY answered_at DESC LIMIT 1');
    $q->execute([$userId]);return $q->fetch(PDO::FETCH_ASSOC)?:null;
}
function murshid_daily_v48_answer_details(PDO $pdo,int $questionId,int $gradeId): ?array {
    $q=$pdo->prepare('SELECT q.correct_answer,q.explanation,q.question_text FROM questions q JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id WHERE q.id=? AND s.grade_id=? LIMIT 1');
    $q->execute([$questionId,$gradeId]);return $q->fetch(PDO::FETCH_ASSOC)?:null;
}
function murshid_daily_v48_submit(PDO $pdo,array $user,array $question,string $answer): array {
    $answer=trim($answer);
    if($answer===''||mb_strlen($answer)>2000)return ['ok'=>false,'message'=>'أدخل إجابتك أولاً (حتى 2000 حرف).'];
    $correct=is_arabic_answer_correct($answer,(string)$question['correct_answer'],(string)$question['type'],(string)($question['question_text']??''));
    try {
        $stmt=$pdo->prepare('INSERT INTO daily_challenge_v48_attempts(user_id,challenge_date,question_id,given_answer,is_correct) VALUES(?,?,?,?,?)');
        $stmt->execute([(int)$user['id'],(new DateTimeImmutable('now',new DateTimeZone('Asia/Baghdad')))->format('Y-m-d'),(int)$question['id'],$answer,(int)$correct]);
    } catch(PDOException $e){
        if((string)$e->getCode()==='23000')return ['ok'=>false,'message'=>'أجبت عن تحدي اليوم مسبقاً. انتظر تحدي الغد.'];
        throw $e;
    }
    return ['ok'=>true,'done'=>true,'score'=>(int)$correct,'total'=>1,'reward'=>0,'correct_answer'=>(string)$question['correct_answer']];
}
