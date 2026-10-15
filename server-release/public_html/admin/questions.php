<?php
declare(strict_types=1);
if (!defined('MURSHID_SKIP_SCHEMA_MAINTENANCE')) define('MURSHID_SKIP_SCHEMA_MAINTENANCE', true);

require_once __DIR__ . '/../config/config.php';
require_once __DIR__ . '/../includes/platform_tools.php';
$admin=require_admin_capability('questions');

// إصلاح ذاتي لبنية قاعدة البيانات قبل فتح بنك الأسئلة، حتى يعمل زر إضافة سؤال
// حتى عند رفع ملفات المرجع فوق قاعدة أقدم.


$pdo = db();
platform_tools_schema($pdo);
$message = '';
$error = '';

/* ---------------------------------------------------------
   مساعدات آمنة
--------------------------------------------------------- */
function q_redirect(string $url = '/admin/questions.php'): never {
    header('Location: ' . BASE_URL . $url);
    exit;
}

function post_int(string $key): int {
    return max(0, (int)($_POST[$key] ?? 0));
}

function get_int(string $key): int {
    return max(0, (int)($_GET[$key] ?? 0));
}

$allowedTypes = ['mcq','true_false','text','fill','meaning','match','genetics','calculation'];
$madTypes = MAD_TYPES();

/* ---------------------------------------------------------
   حذف السؤال — POST فقط
--------------------------------------------------------- */
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'delete') {
    require_post_csrf();
    $id = post_int('question_id');

    if ($id > 0) {
        save_question_revision($pdo,$id,(int)$admin['id'],'delete');
        $stmt = $pdo->prepare("DELETE FROM questions WHERE id=?");
        $stmt->execute([$id]);
        audit_log('حذف سؤال', 'question', (string)$id);
        q_redirect('/admin/questions.php?deleted=1');
    }
    q_redirect();
}

/* ---------------------------------------------------------
   موافقة / رفض سؤال المعلم
--------------------------------------------------------- */
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'moderate') {
    require_post_csrf();

    $id = post_int('question_id');
    $decision = (string)($_POST['decision'] ?? '');
    $note = trim((string)($_POST['moderation_note'] ?? ''));

    if ($id <= 0 || !in_array($decision, ['approve','reject'], true)) {
        $error = 'طلب مراجعة غير صالح.';
    } else {
        $status = $decision === 'approve' ? 'approved' : 'rejected';
        $live = $decision === 'approve' ? 1 : 0;

        $stmt = $pdo->prepare(
            "UPDATE questions
             SET moderation_status=?,
                 verification_status=?,
                 status=?,
                 moderation_note=?
             WHERE id=? AND moderation_status='pending'"
        );
        $stmt->execute([
            $status,
            $decision === 'approve' ? 'verified' : 'review',
            $live,
            $note !== '' ? $note : null,
            $id
        ]);

        if ($stmt->rowCount() > 0) {
            $q = $pdo->prepare("SELECT submitted_by FROM questions WHERE id=? LIMIT 1");
            $q->execute([$id]);
            $teacherId = (int)$q->fetchColumn();

            if ($teacherId > 0) {
                notify_user(
                    $teacherId,
                    $decision === 'approve' ? '✅ تمت الموافقة على سؤالك' : '❌ تم رفض سؤالك',
                    $decision === 'approve'
                        ? 'تم اعتماد السؤال وأصبح متاحاً للطلاب.'
                        : 'تم رفض السؤال من الإدارة.' . ($note !== '' ? ' ملاحظة الإدارة: '.$note : '')
                );
            }

            audit_log(
                $decision === 'approve' ? 'الموافقة على سؤال معلم' : 'رفض سؤال معلم',
                'question',
                (string)$id
            );
            q_redirect('/admin/questions.php?moderated=1');
        }
        $error = 'السؤال غير موجود أو تمت مراجعته مسبقاً.';
    }
}

/* ---------------------------------------------------------
   حفظ سؤال الإدارة
--------------------------------------------------------- */
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'save') {
    require_post_csrf();

    // حماية إضافية: بعض الاستضافات تحتفظ بقاعدة قديمة مع رفع ملفات الموقع فقط.
    // نضمن وجود أعمدة نظام الأسئلة قبل تنفيذ INSERT/UPDATE حتى لا يعود خطأ الحفظ العام.
    try {
        $schemaCheck = $pdo->query("SHOW COLUMNS FROM questions")->fetchAll();
        $schemaCols = [];
        foreach ($schemaCheck as $col) { $schemaCols[(string)$col['Field']] = true; }
        $schemaAdds = [];
        if (!isset($schemaCols['submitted_by'])) $schemaAdds[] = "ADD COLUMN submitted_by INT UNSIGNED NULL AFTER chapter_id";
        if (!isset($schemaCols['moderation_status'])) $schemaAdds[] = "ADD COLUMN moderation_status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'approved' AFTER verification_status";
        if (!isset($schemaCols['moderation_note'])) $schemaAdds[] = "ADD COLUMN moderation_note VARCHAR(500) NULL AFTER moderation_status";
        if (!isset($schemaCols['meaning_word'])) $schemaAdds[] = "ADD COLUMN meaning_word VARCHAR(255) NULL AFTER question_text";
        if (!isset($schemaCols['calculation_json'])) $schemaAdds[] = "ADD COLUMN calculation_json LONGTEXT NULL AFTER genetics_json";
        if (!isset($schemaCols['difficulty'])) $schemaAdds[] = "ADD COLUMN difficulty ENUM('easy','medium','hard') NULL AFTER exam_round";
        if (!isset($schemaCols['question_version'])) $schemaAdds[] = "ADD COLUMN question_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER difficulty";
        if (!isset($schemaCols['content_updated_at'])) $schemaAdds[] = "ADD COLUMN content_updated_at DATETIME NULL AFTER question_version";
        if ($schemaAdds) $pdo->exec("ALTER TABLE questions " . implode(', ', $schemaAdds));
    } catch (Throwable $schemaError) {
        error_log('questions schema sync failed: '.$schemaError->getMessage());
        $error = 'قاعدة البيانات تحتاج مزامنة بسيطة. حدّث الصفحة ثم حاول مرة أخرى.';
    }

    $id = post_int('id');
    $gradeId = post_int('grade_id');
    $subjectId = post_int('subject_id');
    $chapterId = post_int('chapter_id');
    $type = trim((string)($_POST['type'] ?? ''));
    $text = trim((string)($_POST['question_text'] ?? ''));
    $meaningWord = trim((string)($_POST['meaning_word'] ?? ''));
    $correct = trim((string)($_POST['correct_answer'] ?? ''));
    $explanation = trim((string)($_POST['explanation'] ?? ''));
    $geneticsJson = trim((string)($_POST['genetics_json'] ?? ''));
    $calculationJson = trim((string)($_POST['calculation_json'] ?? ''));
    $examYear = (int)($_POST['exam_year'] ?? 0);
    $examRound = trim((string)($_POST['exam_round'] ?? ''));
    $difficulty = trim((string)($_POST['difficulty'] ?? 'medium'));
    if (!in_array($difficulty,['easy','medium','hard'],true)) $difficulty='medium';
    $sourceUrl = trim((string)($_POST['source_url'] ?? ''));
    $verification = ($_POST['verification_status'] ?? 'review') === 'verified' ? 'verified' : 'review';
    $status = isset($_POST['status']) ? 1 : 0;

    $errors = [];

    if ($gradeId <= 0) $errors[] = 'اختر الصف.';
    if ($subjectId <= 0) $errors[] = 'اختر المادة.';
    if ($chapterId <= 0) $errors[] = 'اختر الموضوع.';
    if (!in_array($type, $allowedTypes, true)) $errors[] = 'نوع السؤال غير صالح.';

    if ($type === 'meaning' && $meaningWord === '') {
        $errors[] = 'اكتب الكلمة التي تريد إضافة معناها.';
    }

    if ($text === '' && $type === 'meaning' && $meaningWord !== '') {
        $text = 'ما معنى كلمة: ' . $meaningWord . '؟';
    }
    if ($text === '') $errors[] = 'اكتب نص السؤال.';
    if ($correct === '' && !in_array($type,['match','genetics','calculation'],true)) $errors[] = 'اكتب الإجابة الصحيحة.';
    if($type==='genetics'){
        if($geneticsJson==='') $errors[]='أدخل بيانات المسألة الوراثية.';
        else { $gj=json_decode($geneticsJson,true); if(!is_array($gj) || empty($gj['stages'])) $errors[]='بيانات المسألة الوراثية ليست JSON صالحاً أو لا تحتوي stages.'; }
        if($correct==='') $correct='مسألة وراثية متعددة المراحل';
    }
    if($type==='calculation'){
        if($calculationJson==='') $errors[]='أدخل بيانات المسألة الحسابية التفاعلية.';
        else { $cj=json_decode($calculationJson,true); if(!is_array($cj) || empty($cj['stages'])) $errors[]='بيانات المسألة الحسابية ليست JSON صالحاً أو لا تحتوي stages.'; }
        if($correct==='') $correct='مسألة حسابية متعددة المراحل';
    }
    if ($type === 'match') {
        $correct = 'مطابقة';
        $wordsCheck = is_array($_POST['match_word'] ?? null) ? $_POST['match_word'] : [];
        $typesCheck = is_array($_POST['match_type'] ?? null) ? $_POST['match_type'] : [];
        $reasonsCheck = is_array($_POST['match_reason'] ?? null) ? $_POST['match_reason'] : [];
        $validMatchRows = 0;
        foreach($wordsCheck as $i=>$w){
            $w=trim((string)$w);$mt=normalize_mad_type(trim((string)($typesCheck[$i]??'')));$rs=trim((string)($reasonsCheck[$i]??''));
            if($w==='' && $mt==='' && $rs==='') continue;
            if($w==='') $errors[]='اكتب الكلمة في سؤال أحكام التلاوة.';
            if($mt==='' || !in_array($mt,$madTypes,true)) $errors[]='اختر نوع المد الصحيح.';
            if($rs==='') $errors[]='اكتب سبب نوع المد.';
            if($w!=='' && in_array($mt,$madTypes,true) && $rs!=='') $validMatchRows++;
        }
        if($validMatchRows===0) $errors[]='أضف كلمة واحدة على الأقل مع نوع المد والسبب.';
    }

    if ($examYear < 0 || $examYear > 2100) $examYear = 0;

    if ($sourceUrl !== '' && !filter_var($sourceUrl, FILTER_VALIDATE_URL)) {
        $errors[] = 'رابط مصدر السؤال غير صالح.';
    }

    /* أهم حماية من ربط السؤال بمادة/صف خاطئ */
    if (!$errors) {
        $check = $pdo->prepare(
            "SELECT c.id
             FROM chapters c
             JOIN subjects s ON s.id=c.subject_id
             WHERE c.id=? AND s.id=? AND s.grade_id=? AND s.status=1 AND c.status=1
             LIMIT 1"
        );
        $check->execute([$chapterId, $subjectId, $gradeId]);
        if (!$check->fetchColumn()) {
            $errors[] = 'الموضوع المختار لا يتبع المادة والصف المحددين.';
        }
    }

    // Stop newly saved exact-content duplicates, including different question types.
    // Retain same wording if the MODEL ANSWER differs: these are conflicts for review.
    if (!$errors) {
        $normalizeQuestion = static function (string $value): string {
            $value = preg_replace('/[\x{0640}\x{200C}\x{200E}\x{200F}\x{202A}-\x{202E}]/u', '', $value) ?? $value;
            $value = preg_replace('/\s+/u', ' ', trim($value)) ?? trim($value);
            return rtrim($value, " \t\n\r\0\x0B.،؛؟!?");
        };
        $candidateText = $normalizeQuestion($text);
        $candidateAnswer = $normalizeQuestion($correct);
        $duplicateScan = $pdo->prepare(
            "SELECT id,question_text,correct_answer FROM questions
             WHERE chapter_id=? AND id<>? AND is_active=1 AND status=1 AND moderation_status='approved'"
        );
        $duplicateScan->execute([$chapterId,$id]);
        while ($existing = $duplicateScan->fetch(PDO::FETCH_ASSOC)) {
            if ($normalizeQuestion((string)$existing['question_text']) === $candidateText
                && $normalizeQuestion((string)$existing['correct_answer']) === $candidateAnswer) {
                $errors[] = 'السؤال موجود مسبقاً برقم '.(int)$existing['id'].'. افتح السؤال الموجود بدلاً من إضافة نسخة أخرى.';
                break;
            }
        }
        $duplicateScan->closeCursor();
    }

    if (!$errors) {
        try {
            $pdo->beginTransaction();

            if ($id > 0) {
                save_question_revision($pdo,$id,(int)$admin['id'],'update');
                $stmt = $pdo->prepare(
                    "UPDATE questions
                     SET chapter_id=?, type=?, question_text=?, meaning_word=?,
                         correct_answer=?, explanation=?, genetics_json=?, calculation_json=?, exam_year=?, exam_round=?,
                         source_url=?, verification_status=?, difficulty=?, status=?, question_version=question_version+1, content_updated_at=NOW()
                     WHERE id=?"
                );
                $stmt->execute([
                    $chapterId,
                    $type,
                    $text,
                    $meaningWord !== '' ? $meaningWord : null,
                    $correct,
                    $explanation !== '' ? $explanation : null,
                    $type==='genetics' ? $geneticsJson : null,
                    $type==='calculation' ? $calculationJson : null,
                    $examYear > 0 ? $examYear : null,
                    $examRound !== '' ? $examRound : null,
                    $sourceUrl !== '' ? $sourceUrl : null,
                    $verification,
                    $difficulty,
                    $status,
                    $id
                ]);
                $savedId = $id;
                audit_log('تعديل سؤال', 'question', (string)$id);
            } else {
                $stmt = $pdo->prepare(
                    "INSERT INTO questions
                    (chapter_id,type,question_text,meaning_word,correct_answer,explanation,genetics_json,calculation_json,
                     exam_year,exam_round,difficulty,question_version,content_updated_at,source_url,verification_status,
                     moderation_status,moderation_note,submitted_by,status,sort_order)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,1,NOW(),?,?,?,?,?,?,?)"
                );
                $stmt->execute([
                    $chapterId,
                    $type,
                    $text,
                    $meaningWord !== '' ? $meaningWord : null,
                    $correct,
                    $explanation !== '' ? $explanation : null,
                    $type==='genetics' ? $geneticsJson : null,
                    $type==='calculation' ? $calculationJson : null,
                    $examYear > 0 ? $examYear : null,
                    $examRound !== '' ? $examRound : null,
                    $difficulty,
                    $sourceUrl !== '' ? $sourceUrl : null,
                    $verification,
                    'approved',
                    null,
                    null,
                    $status,
                    9999
                ]);
                $savedId = (int)$pdo->lastInsertId();
                save_question_revision($pdo,$savedId,(int)$admin['id'],'create');
                audit_log('إضافة سؤال', 'question', (string)$savedId);
            }

            /* خيارات الاختيار من متعدد / المعنى / صح وخطأ */
            $pdo->prepare("DELETE FROM answers WHERE question_id=?")->execute([$savedId]);

            if (in_array($type, ['mcq','meaning'], true)) {
                $options = $_POST['options'] ?? [];
                if (is_array($options)) {
                    $insert = $pdo->prepare(
                        "INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)"
                    );
                    foreach ($options as $i => $option) {
                        $option = trim((string)$option);
                        if ($option === '') continue;
                        $insert->execute([$savedId, $option, (int)$i]);
                    }
                }
            } elseif ($type === 'true_false') {
                $insert = $pdo->prepare(
                    "INSERT INTO answers(question_id,answer_text,sort_order) VALUES(?,?,?)"
                );
                $insert->execute([$savedId, 'صح', 0]);
                $insert->execute([$savedId, 'خطأ', 1]);
            }

            /* عناصر المطابقة */
            $pdo->prepare("DELETE FROM match_items WHERE question_id=?")->execute([$savedId]);

            if ($type === 'match') {
                $words = is_array($_POST['match_word'] ?? null) ? $_POST['match_word'] : [];
                $types = is_array($_POST['match_type'] ?? null) ? $_POST['match_type'] : [];
                $reasons = is_array($_POST['match_reason'] ?? null) ? $_POST['match_reason'] : [];

                $insert = $pdo->prepare(
                    "INSERT INTO match_items(question_id,mad_word,correct_type,reason_text,sort_order)
                     VALUES(?,?,?,?,?)"
                );

                foreach ($words as $i => $word) {
                    $word = trim((string)$word);
                    $madType = normalize_mad_type(trim((string)($types[$i] ?? '')));
                    $reason = trim((string)($reasons[$i] ?? ''));
                    if ($word === '' || $madType === '') continue;
                    if (!in_array($madType, $madTypes, true)) continue;
                    $insert->execute([$savedId, $word, $madType, $reason !== '' ? $reason : null, (int)$i]);
                }
            }

            $pdo->commit();
            q_redirect('/admin/questions.php?saved=1');
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            error_log('admin questions save failed: '.$e->getMessage());
            $error = 'تعذر حفظ السؤال حالياً. لم يتم فقدان البيانات. تم تسجيل سبب الخطأ في سجل النظام.';
        }
    } else {
        $error = implode(' ', $errors);
    }
}

/* ---------------------------------------------------------
   البيانات: الصفوف الموجودة فعلياً فقط
--------------------------------------------------------- */
$grades = $pdo->query(
    "SELECT id,name,sort_order
     FROM grades WHERE name IN ('الثالث المتوسط','السادس العلمي','السادس الأدبي') ORDER BY FIELD(name,'الثالث المتوسط','السادس العلمي','السادس الأدبي')"
)->fetchAll();

$subjects = $pdo->query(
    "SELECT id,grade_id,name,sort_order
     FROM subjects
     WHERE status=1
     ORDER BY grade_id,sort_order,id"
)->fetchAll();

$chapters = $pdo->query(
    "SELECT c.id,c.subject_id,c.name,c.sort_order
     FROM chapters c
     JOIN subjects s ON s.id=c.subject_id
     WHERE c.status=1 AND s.status=1
     ORDER BY c.subject_id,c.sort_order,c.id"
)->fetchAll();

/* ---------------------------------------------------------
   تحرير سؤال
--------------------------------------------------------- */
$editId = get_int('edit');
$edit = null;
$editAnswers = [];
$editMatches = [];

if ($editId > 0) {
    $stmt = $pdo->prepare(
        "SELECT q.*,c.subject_id,s.grade_id
         FROM questions q
         JOIN chapters c ON c.id=q.chapter_id
         JOIN subjects s ON s.id=c.subject_id
         WHERE q.id=? LIMIT 1"
    );
    $stmt->execute([$editId]);
    $edit = $stmt->fetch() ?: null;

    if ($edit) {
        $stmt = $pdo->prepare("SELECT answer_text FROM answers WHERE question_id=? ORDER BY sort_order,id");
        $stmt->execute([$editId]);
        $editAnswers = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $stmt = $pdo->prepare("SELECT mad_word,correct_type,reason_text FROM match_items WHERE question_id=? ORDER BY sort_order,id");
        $stmt->execute([$editId]);
        $editMatches = $stmt->fetchAll();
    }
}

/* ---------------------------------------------------------
   فلاتر القائمة
--------------------------------------------------------- */
$filterGrade = get_int('grade');
$filterSubject = get_int('subject');
$filterChapter = get_int('chapter');
$search = trim((string)($_GET['q'] ?? ''));
$page = max(1, get_int('page') ?: 1);
$perPage = 40;

$where = [];
$params = [];

if ($filterGrade > 0) {
    $where[] = 's.grade_id=?';
    $params[] = $filterGrade;
}
if ($filterSubject > 0) {
    $where[] = 's.id=?';
    $params[] = $filterSubject;
}
if ($filterChapter > 0) {
    $where[] = 'c.id=?';
    $params[] = $filterChapter;
}
if ($search !== '') {
    $where[] = '(q.question_text LIKE ? OR q.correct_answer LIKE ? OR q.meaning_word LIKE ?)';
    $like = '%'.$search.'%';
    array_push($params,$like,$like,$like);
}

$whereSql = $where ? 'WHERE '.implode(' AND ',$where) : '';

$countStmt = $pdo->prepare(
    "SELECT COUNT(*)
     FROM questions q
     JOIN chapters c ON c.id=q.chapter_id
     JOIN subjects s ON s.id=c.subject_id
     JOIN grades g ON g.id=s.grade_id
     $whereSql"
);
$countStmt->execute($params);
$total = (int)$countStmt->fetchColumn();
$totalPages = max(1,(int)ceil($total/$perPage));
$page = min($page,$totalPages);
$offset = ($page-1)*$perPage;

$listStmt = $pdo->prepare(
    "SELECT q.id,q.type,q.question_text,q.meaning_word,q.correct_answer,
            q.exam_year,q.exam_round,q.difficulty,q.question_version,q.verification_status,q.status,
            q.moderation_status,q.created_at,
            c.name chapter_name,s.id subject_id,s.name subject_name,
            g.id grade_id,g.name grade_name
     FROM questions q
     JOIN chapters c ON c.id=q.chapter_id
     JOIN subjects s ON s.id=c.subject_id
     JOIN grades g ON g.id=s.grade_id
     $whereSql
     ORDER BY g.sort_order,s.sort_order,c.sort_order,q.id DESC
     LIMIT $perPage OFFSET $offset"
);
$listStmt->execute($params);
$questions = $listStmt->fetchAll();

/* الأسئلة المعلقة */
$pending = $pdo->query(
    "SELECT q.id,q.question_text,q.correct_answer,q.explanation,q.created_at,
            q.moderation_note,u.full_name teacher_name,
            g.name grade_name,s.name subject_name,c.name chapter_name
     FROM questions q
     JOIN chapters c ON c.id=q.chapter_id
     JOIN subjects s ON s.id=c.subject_id
     JOIN grades g ON g.id=s.grade_id
     LEFT JOIN users u ON u.id=q.submitted_by
     WHERE q.moderation_status='pending'
     ORDER BY q.id DESC
     LIMIT 100"
)->fetchAll();

$flash = '';
if (isset($_GET['saved'])) $flash = 'تم حفظ السؤال بنجاح.';
if (isset($_GET['deleted'])) $flash = 'تم حذف السؤال.';
if (isset($_GET['moderated'])) $flash = 'تم تحديث حالة السؤال.';

$subjectsJson = json_encode($subjects, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
$chaptersJson = json_encode($chapters, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
$editJson = json_encode([
    'grade_id'=>(int)($edit['grade_id'] ?? 0),
    'subject_id'=>(int)($edit['subject_id'] ?? 0),
    'chapter_id'=>(int)($edit['chapter_id'] ?? 0),
], JSON_UNESCAPED_UNICODE);
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>إدارة الأسئلة | منصة المرشد الوزاري</title>

<script src="<?=BASE_URL?>/assets/js/theme-init.js?v=20260928-v56"></script><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/platform.css?v=20260929-v584contrast"><link rel="stylesheet" href="<?= BASE_URL ?>/assets/css/brand-v57.css?v=20260929-v5811responsive">

<link rel="icon" type="image/png" sizes="512x512" href="<?= BASE_URL ?>/assets/img/site-icon-512.png?v=20260928-v56"><link rel="apple-touch-icon" sizes="180x180" href="<?= BASE_URL ?>/assets/img/apple-touch-icon.png?v=20260928-v56">
</head>
<body class="page admin-theme admin-responsive-page platform-admin" data-es-role="admin" data-es-base="..">
<?php platform_bar('admin'); ?>
<div class="qm-wrap">

<?php
$qStats = [
    'total'    => (int) db()->query("SELECT COUNT(*) FROM questions WHERE status=1")->fetchColumn(),
    'verified' => (int) db()->query("SELECT COUNT(*) FROM questions WHERE status=1 AND verification_status='verified'")->fetchColumn(),
    'pending'  => (int) db()->query("SELECT COUNT(*) FROM questions WHERE moderation_status='pending'")->fetchColumn(),
    'chapters' => (int) db()->query("SELECT COUNT(*) FROM chapters WHERE status=1")->fetchColumn(),
];
?>
<div class="ops-stats">
  <div class="ops-stat"><b><?= $qStats['total'] ?></b><span>سؤال منشور</span></div>
  <div class="ops-stat"><b><?= $qStats['verified'] ?></b><span>موثّق المصدر</span></div>
  <div class="ops-stat"><b><?= $qStats['pending'] ?></b><span>بانتظار المراجعة</span></div>
  <div class="ops-stat"><b><?= $qStats['chapters'] ?></b><span>فصل دراسي</span></div>
</div>
<header class="qm-head">
  <div class="qm-head-row">
    <div>
      <h1 class="qm-title">بنك الأسئلة</h1>
      <p class="qm-sub">اختر الصف أولاً، ثم تظهر مواده فقط، ثم الموضوع — لتصل إلى السؤال المطلوب بدون قوائم طويلة.</p>
    </div>
    <div class="qm-actions">
      <a class="qm-btn" href="question-quality.php">تدقيق الجودة والتكرار</a>
      <a class="qm-btn" href="dashboard.php">← لوحة الإدارة</a>
      <a class="qm-btn" href="../logout.php?scope=admin">خروج</a>
    </div>
  </div>
</header>

<?php if ($flash): ?><div class="qm-alert ok"><?=e($flash)?></div><?php endif; ?>
<?php if ($error): ?><div class="qm-alert err"><?=e($error)?></div><?php endif; ?>

<section class="qm-card">
  <div class="qm-head-row">
    <div>
      <h2><?= $edit ? ui_icon('edit').' تعديل السؤال' : ui_icon('plus').' إضافة سؤال جديد' ?></h2>
      <p class="qm-muted">الترابط إجباري: الصف ← المادة ← الموضوع ← السؤال.</p>
    </div>
    <?php if ($edit): ?><a class="qm-secondary" href="questions.php">إلغاء التعديل</a><?php endif; ?>
  </div>

  <form method="post" id="questionForm">
    <input type="hidden" name="csrf" value="<?=e(csrf_token())?>">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<?= (int)($edit['id'] ?? 0) ?>">

    <div class="qm-grid" style="margin-top:17px">
      <div class="qm-field">
        <label>① الصف</label>
        <select name="grade_id" id="gradeSelect" class="el-pill" required>
          <option value="">اختر الصف</option>
          <?php foreach ($grades as $g): ?>
            <option value="<?=$g['id']?>" <?=((int)($edit['grade_id']??0)===(int)$g['id']?'selected':'')?>><?=e($g['name'])?></option>
          <?php endforeach; ?>
        </select>
      </div>

      <div class="qm-field">
        <label>② المادة</label>
        <select name="subject_id" id="subjectSelect" class="el-pill" required disabled>
          <option value="">اختر الصف أولاً</option>
        </select>
      </div>

      <div class="qm-field">
        <label>③ الموضوع</label>
        <select name="chapter_id" id="chapterSelect" class="el-pill" required disabled>
          <option value="">اختر المادة أولاً</option>
        </select>
      </div>

      <div class="qm-field">
        <label>نوع السؤال</label>
        <select name="type" id="typeSelect" class="el-pill" required>
          <?php
          $currentType = $edit['type'] ?? 'mcq';
          $typeNames = ['mcq'=>'اختيار من متعدد','true_false'=>'صح وخطأ','text'=>'شرح نصي','fill'=>'إكمال فراغ','meaning'=>'معنى كلمة — اختيارات','match'=>'أحكام التلاوة — نوع المد والسبب','genetics'=>'مسألة وراثية تفاعلية','calculation'=>'الحل الوزاري التفاعلي'];
          foreach ($typeNames as $k=>$v): ?>
            <option value="<?=$k?>" <?=($currentType===$k?'selected':'')?>><?=e($v)?></option>
          <?php endforeach; ?>
        </select>
      </div>

      <div class="qm-field qm-full" id="meaningWrap">
        <label>الكلمة</label>
        <input name="meaning_word" value="<?=e($edit['meaning_word'] ?? '')?>" placeholder="مثال: السؤدد">
        <p class="qm-muted" style="font-size:12px;margin:6px 0 0">في سؤال المعاني: اكتب الكلمة، ويمكن للطالب أن يختار معناها الصحيح من الخيارات.</p>
      </div>

      <div class="qm-field qm-full">
        <label>نص السؤال</label>
        <textarea name="question_text" placeholder="اكتب نص السؤال هنا..."><?=e($edit['question_text'] ?? '')?></textarea>
      </div>

      <div class="qm-field qm-full" id="correctWrap">
        <label>الإجابة الصحيحة</label>
        <input name="correct_answer" value="<?=e($edit['correct_answer'] ?? '')?>" placeholder="الإجابة الصحيحة">
      </div>

      <div class="qm-field qm-full" id="geneticsWrap">
        <label>بيانات المسألة الوراثية (JSON)</label>
        <textarea name="genetics_json" rows="10" placeholder='{"stages":[...]}' style="direction:ltr;text-align:left"><?=e((string)($edit['genetics_json'] ?? ''))?></textarea>
        <p class="qm-muted" style="font-size:12px;margin:6px 0 0">يُستخدم للمسائل متعددة المراحل: الرموز، الأبوين، الأمشاج، مربع بانيت والنتيجة.</p>
      </div>


      <div class="qm-field qm-full" id="calculationWrap">
        <label>الحل الوزاري التفاعلي</label>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin:8px 0 10px">
          <button type="button" class="qm-secondary" onclick="loadCalcTemplate('physics')">قالب فيزياء</button>
          <button type="button" class="qm-secondary" onclick="loadCalcTemplate('chemistry')">قالب كيمياء</button>
          <button type="button" class="qm-secondary" onclick="loadCalcTemplate('math')">قالب رياضيات</button>
        </div>
        <textarea id="calculationJson" name="calculation_json" rows="18" placeholder='{"subject":"physics","stages":[...]}' style="direction:ltr;text-align:left"><?=e((string)($edit['calculation_json'] ?? ''))?></textarea>
        <p class="qm-muted" style="font-size:12px;margin:6px 0 0">لا يوجد وضع «حل على ورقة». المسألة داخل الموقع فقط: المعطيات ← القانون/القاعدة ← تحويل الوحدات عند الحاجة ← التعويض ← العمليات ← الناتج النهائي. كل مرحلة يمكن تصحيحها مستقلة، والمرحلة التالية تبقى مقفلة حتى صحة السابقة.</p>
      </div>

      <div class="qm-field qm-full" id="optionsWrap">
        <label>خيارات الإجابة</label>
        <div class="qm-options">
          <?php for($i=0;$i<4;$i++): ?>
            <input name="options[]" value="<?=e($editAnswers[$i] ?? '')?>" placeholder="الخيار <?=$i+1?>">
          <?php endfor; ?>
        </div>
      </div>

      <div class="qm-field qm-full" id="matchWrap">
        <label>عناصر المطابقة</label>
        <div id="matchRows">
          <?php foreach ($editMatches as $m): ?>
            <div class="qm-match-row">
              <input name="match_word[]" value="<?=e($m['mad_word'])?>" placeholder="الكلمة">
              <select name="match_type[]">
                <option value="">اختر النوع</option>
                <?php foreach($madTypes as $mt): ?><option value="<?=e($mt)?>" <?=($m['correct_type']===$mt?'selected':'')?>><?=e($mt)?></option><?php endforeach; ?>
              </select>
              <input name="match_reason[]" value="<?=e($m['reason_text'])?>" placeholder="السبب">
              <button type="button" class="qm-remove" onclick="this.parentElement.remove()">حذف</button>
            </div>
          <?php endforeach; ?>
        </div>
        <button type="button" class="qm-secondary" onclick="addMatchRow()" style="margin-top:7px">＋ إضافة عنصر</button>
      </div>

      <div class="qm-field">
        <label>الشرح المختصر</label>
        <input name="explanation" value="<?=e($edit['explanation'] ?? '')?>" placeholder="اختياري">
      </div>

      <div class="qm-field">
        <label>سنة الامتحان</label>
        <input type="number" name="exam_year" min="2000" max="2100" value="<?=e((string)($edit['exam_year'] ?? ''))?>" placeholder="2025">
      </div>

      <div class="qm-field">
        <label>الدور</label>
        <input name="exam_round" value="<?=e($edit['exam_round'] ?? '')?>" placeholder="الدور الأول">
      </div>

      <div class="qm-field">
        <label>الصعوبة</label>
        <select name="difficulty" class="el-pill">
          <option value="easy" <?=($edit['difficulty']??'medium')==='easy'?'selected':''?>>سهل</option>
          <option value="medium" <?=($edit['difficulty']??'medium')==='medium'?'selected':''?>>متوسط</option>
          <option value="hard" <?=($edit['difficulty']??'medium')==='hard'?'selected':''?>>صعب</option>
        </select>
      </div>

      <div class="qm-field qm-full">
        <label>مصدر السؤال</label>
        <input type="url" name="source_url" value="<?=e($edit['source_url'] ?? '')?>" placeholder="https://example.com">
      </div>

      <div class="qm-field">
        <label>التحقق</label>
        <select name="verification_status" class="el-pill">
          <option value="verified" <?=($edit['verification_status']??'review')==='verified'?'selected':''?>>موثق / متحقق</option>
          <option value="review" <?=($edit['verification_status']??'review')!=='verified'?'selected':''?>>يحتاج مراجعة</option>
        </select>
      </div>

      <div class="qm-field">
        <label>حالة السؤال</label>
        <label style="display:flex;gap:8px;align-items:center;margin-top:13px">
          <input type="checkbox" name="status" data-el-toggle data-label="منشور للطلاب" aria-label="نشر السؤال للطلاب" <?=((int)($edit['status'] ?? 1)===1?'checked':'')?> style="width:auto">
          <span>منشور للطلاب</span>
        </label>
      </div>

      <div class="qm-full">
        <button class="qm-submit" type="submit"><?= $edit ? 'حفظ التعديلات' : 'حفظ السؤال' ?></button>
      </div>
    </div>
  </form>
</section>

<section class="qm-card qm-mod">
  <div class="qm-head-row">
    <div>
      <h2><?=ui_icon('shield')?> مراجعة أسئلة المعلمين</h2>
      <p class="qm-muted">لا يظهر سؤال المعلم للطلاب حتى تتم الموافقة عليه.</p>
    </div>
    <strong class="qm-pending-count"><?=count($pending)?></strong>
  </div>

  <?php if (!$pending): ?>
    <div class="qm-empty">لا توجد أسئلة معلقة حالياً.</div>
  <?php else: foreach($pending as $pq): ?>
    <article class="qm-pending">
      <div class="qm-pending-head">
        <div>
          <b><?=e($pq['teacher_name'] ?: 'معلم')?></b>
          <small><?=e($pq['grade_name'])?> ← <?=e($pq['subject_name'])?> ← <?=e($pq['chapter_name'])?></small>
        </div>
        <span class="qm-pill wait">قيد المراجعة</span>
      </div>
      <div class="qm-pending-q"><?=nl2br(e($pq['question_text']))?></div>
      <div class="qm-answer-line"><b>الإجابة:</b> <?=e($pq['correct_answer'])?></div>
      <form method="post" class="qm-mod-form">
        <input type="hidden" name="csrf" value="<?=e(csrf_token())?>">
        <input type="hidden" name="action" value="moderate">
        <input type="hidden" name="question_id" value="<?=$pq['id']?>">
        <input name="moderation_note" maxlength="500" placeholder="ملاحظة الرفض (اختياري)">
        <button class="qm-approve" type="submit" name="decision" value="approve"><?=ui_icon('check')?> موافقة ونشر</button>
        <button class="qm-reject" type="submit" name="decision" value="reject" onclick="return confirm('رفض السؤال؟')">✕ رفض</button>
      </form>
    </article>
  <?php endforeach; endif; ?>
</section>

<section class="qm-card">
  <div class="qm-head-row">
    <div>
      <h2><?=ui_icon('search')?> البحث في بنك الأسئلة</h2>
      <p class="qm-muted">اختر الصف ثم المادة لتقليل النتائج، أو ابحث بالكلمة.</p>
    </div>
    <span class="qm-count"><?=$total?> سؤال</span>
  </div>

  <form method="get" class="qm-toolbar">
    <div class="qm-field">
      <label>الصف</label>
      <select name="grade" id="filterGrade" class="el-pill">
        <option value="">كل الصفوف الموجودة</option>
        <?php foreach($grades as $g): ?><option value="<?=$g['id']?>" <?=($filterGrade===$g['id']?'selected':'')?>><?=e($g['name'])?></option><?php endforeach; ?>
      </select>
    </div>
    <div class="qm-field">
      <label>المادة</label>
      <select name="subject" id="filterSubject" class="el-pill">
        <option value="">كل المواد</option>
      </select>
    </div>
    <div class="qm-field">
      <label>الموضوع</label>
      <select name="chapter" id="filterChapter" class="el-pill">
        <option value="">كل المواضيع</option>
      </select>
    </div>
    <div class="qm-field">
      <label>بحث</label>
      <input name="q" value="<?=e($search)?>" placeholder="نص السؤال أو الإجابة أو الكلمة">
    </div>
    <button class="qm-submit" type="submit">بحث</button>
    <a class="qm-secondary" href="questions.php">مسح الفلاتر</a>
  </form>
</section>

<section class="qm-card">
  <div class="section-actions" style="display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:12px">
    <h2 style="margin:0"><?=ui_icon('book')?> الأسئلة الحالية</h2>
    <span class="qm-muted">صفحة <?=$page?> من <?=$totalPages?></span>
  </div>

  <?php if (!$questions): ?>
    <div class="admin-empty">لا توجد أسئلة مطابقة للبحث.</div>
  <?php else: ?>
  <div class="qm-table">
    <table>
      <thead><tr>
        <th>السؤال</th><th>الصف / المادة / الموضوع</th><th>النوع</th>
        <th>التحقق</th><th>الحالة</th><th>الإجراء</th>
      </tr></thead>
      <tbody>
      <?php foreach($questions as $q): ?>
        <tr>
          <td class="qm-question">
            <b><?=e(mb_strimwidth((string)$q['question_text'],0,150,'…','UTF-8'))?></b>
            <?php if($q['meaning_word']): ?><div class="qm-muted">الكلمة: <?=e($q['meaning_word'])?></div><?php endif; ?>
          </td>
          <td>
            <span class="qm-path"><?=e($q['grade_name'])?></span><br>
            <span class="qm-muted"><?=e($q['subject_name'])?> ← <?=e($q['chapter_name'])?></span>
          </td>
          <td><?=e($typeNames[$q['type']] ?? $q['type'])?></td>
          <td>
            <?php if($q['verification_status']==='verified'): ?><span class="qm-pill ok">موثق</span>
            <?php else: ?><span class="qm-pill wait">مراجعة</span><?php endif; ?>
          </td>
          <td>
            <?php if((int)$q['status']===1): ?><span class="qm-pill ok">منشور</span>
            <?php else: ?><span class="qm-pill no">مخفي</span><?php endif; ?>
          </td>
          <td>
            <div style="display:flex;gap:6px;flex-wrap:wrap">
              <a class="qm-secondary" href="?edit=<?=$q['id']?>">تعديل</a>
              <form method="post" onsubmit="return confirm('حذف السؤال نهائياً؟')" style="margin:0">
                <input type="hidden" name="csrf" value="<?=e(csrf_token())?>">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="question_id" value="<?=$q['id']?>">
                <button class="qm-remove" type="submit">حذف</button>
              </form>
            </div>
          </td>
        </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
  </div>

  <?php
  $base = 'questions.php?grade='.urlencode((string)$filterGrade).'&subject='.urlencode((string)$filterSubject).'&chapter='.urlencode((string)$filterChapter).'&q='.urlencode($search).'&page=';
  ?>
  <div class="qm-pages">
    <?php if($page>1): ?><a href="<?=$base.($page-1)?>">السابق</a><?php endif; ?>
    <?php
      $start=max(1,$page-2); $end=min($totalPages,$page+2);
      for($i=$start;$i<=$end;$i++):
    ?>
      <?php if($i===$page): ?><span class="on"><?=$i?></span><?php else: ?><a href="<?=$base.$i?>"><?=$i?></a><?php endif; ?>
    <?php endfor; ?>
    <?php if($page<$totalPages): ?><a href="<?=$base.($page+1)?>">التالي</a><?php endif; ?>
  </div>
  <?php endif; ?>
</section>

</div>

<script>
const SUBJECTS = <?=$subjectsJson ?: '[]'?>;
const CHAPTERS = <?=$chaptersJson ?: '[]'?>;
const EDIT = <?=$editJson ?: '{}'?>;

function fillSubjects(gradeId, select, selectedId=0, includeAll=false){
  select.innerHTML='';
  const first=document.createElement('option');
  first.value='';
  first.textContent=includeAll ? 'كل المواد' : (gradeId ? 'اختر المادة' : 'اختر الصف أولاً');
  select.appendChild(first);
  if(!gradeId){select.disabled=true;return;}
  SUBJECTS.filter(s=>Number(s.grade_id)===Number(gradeId)).forEach(s=>{
    const o=document.createElement('option');
    o.value=s.id;o.textContent=s.name;
    if(Number(s.id)===Number(selectedId)) o.selected=true;
    select.appendChild(o);
  });
  select.disabled=false;
}

function fillChapters(subjectId, select, selectedId=0, includeAll=false){
  select.innerHTML='';
  const first=document.createElement('option');
  first.value='';
  first.textContent=includeAll ? 'كل المواضيع' : (subjectId ? 'اختر الموضوع' : 'اختر المادة أولاً');
  select.appendChild(first);
  if(!subjectId){select.disabled=true;return;}
  CHAPTERS.filter(c=>Number(c.subject_id)===Number(subjectId)).forEach(c=>{
    const o=document.createElement('option');
    o.value=c.id;o.textContent=c.name;
    if(Number(c.id)===Number(selectedId)) o.selected=true;
    select.appendChild(o);
  });
  select.disabled=false;
}

const gradeSelect=document.getElementById('gradeSelect');
const subjectSelect=document.getElementById('subjectSelect');
const chapterSelect=document.getElementById('chapterSelect');

function syncForm(){
  fillSubjects(gradeSelect.value,subjectSelect,EDIT.subject_id||0,false);
  fillChapters(subjectSelect.value,chapterSelect,EDIT.chapter_id||0,false);
}
gradeSelect.addEventListener('change',()=>{
  EDIT.subject_id=0;EDIT.chapter_id=0;
  fillSubjects(gradeSelect.value,subjectSelect,0,false);
  fillChapters('',chapterSelect,0,false);
});
subjectSelect.addEventListener('change',()=>{
  EDIT.chapter_id=0;
  fillChapters(subjectSelect.value,chapterSelect,0,false);
});
syncForm();

const filterGrade=document.getElementById('filterGrade');
const filterSubject=document.getElementById('filterSubject');
const filterChapter=document.getElementById('filterChapter');
const selectedFilterSubject=<?=json_encode($filterSubject)?>;
const selectedFilterChapter=<?=json_encode($filterChapter)?>;

function syncFilters(){
  fillSubjects(filterGrade.value,filterSubject,selectedFilterSubject,true);
  fillChapters(filterSubject.value,filterChapter,selectedFilterChapter,true);
}
filterGrade.addEventListener('change',()=>{
  filterSubject.value='';
  filterChapter.value='';
  fillSubjects(filterGrade.value,filterSubject,0,true);
  fillChapters('',filterChapter,0,true);
});
filterSubject.addEventListener('change',()=>{
  filterChapter.value='';
  fillChapters(filterSubject.value,filterChapter,0,true);
});
syncFilters();

function addMatchRow(word='',type='',reason=''){
  const wrap=document.getElementById('matchRows');
  const row=document.createElement('div');
  row.className='qm-match-row';
  const options=<?=json_encode($madTypes,JSON_UNESCAPED_UNICODE)?>.map(t=>`<option value="${escapeHtml(t)}" ${t===type?'selected':''}>${escapeHtml(t)}</option>`).join('');
  row.innerHTML=`<input name="match_word[]" value="${escapeAttr(word)}" placeholder="الكلمة">
    <select name="match_type[]" class="el-pill"><option value="">اختر النوع</option>${options}</select>
    <input name="match_reason[]" value="${escapeAttr(reason)}" placeholder="السبب">
    <button type="button" class="qm-remove" onclick="this.parentElement.remove()">حذف</button>`;
  wrap.appendChild(row);
  if(window.ElLists) window.ElLists.refresh();
}
function escapeHtml(s){return String(s).replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]))}
function escapeAttr(s){return escapeHtml(s)}
<?php if(!$editMatches): ?>addMatchRow();<?php endif; ?>


function loadCalcTemplate(subject){
  const ta=document.getElementById('calculationJson'); if(!ta) return;
  const common={verified:false,instructions:'حلّ المسألة داخل الموقع خطوة بخطوة. لا تنتقل للمرحلة التالية قبل تثبيت المرحلة الحالية.',stages:[]};
  if(subject==='physics'){
    common.subject='physics';
    common.stages=[
      {key:'givens',label:'المعطيات مع الوحدات',kind:'text',answers:['اكتب القيم الموثقة هنا']},
      {key:'law',label:'اختيار القانون',kind:'choice',answer:'law1',options:[{value:'law1',label:'ضع القانون الصحيح هنا'},{value:'law2',label:'قانون مشتت 1'},{value:'law3',label:'قانون مشتت 2'}]},
      {key:'conversion',label:'تحويل الوحدات',kind:'text',answers:['ضع التحويل الصحيح هنا']},
      {key:'substitution',label:'التعويض',kind:'text',answers:['ضع التعويض الصحيح هنا']},
      {key:'operations',label:'العمليات الحسابية',kind:'text',answers:['ضع العمليات أو القيمة الوسيطة الصحيحة هنا']},
      {key:'result',label:'الناتج النهائي',kind:'number',answer:'0',unit:'الوحدة',tolerance:0.001,display_answer:'ضع الجواب الصحيح'}
    ];
  }else if(subject==='chemistry'){
    common.subject='chemistry';
    common.stages=[
      {key:'givens',label:'المعطيات',kind:'text',answers:['ضع المعطيات الصحيحة']},
      {key:'law',label:'المعادلة أو القانون',kind:'choice',answer:'law1',options:[{value:'law1',label:'ضع القانون/المعادلة الصحيحة'},{value:'law2',label:'مشتت 1'},{value:'law3',label:'مشتت 2'}]},
      {key:'balance',label:'الموازنة إن وجدت',kind:'text',answers:['ضع الموازنة الصحيحة']},
      {key:'ratio',label:'النسبة المولية/الكتلية',kind:'text',answers:['ضع النسبة الصحيحة']},
      {key:'calculation',label:'الحساب',kind:'text',answers:['ضع الحساب الصحيح']},
      {key:'result',label:'الجواب النهائي',kind:'number',answer:'0',unit:'الوحدة',tolerance:0.001,display_answer:'ضع الجواب الصحيح'}
    ];
  }else{
    common.subject='math';
    common.stages=[
      {key:'givens',label:'المعطيات',kind:'text',answers:['ضع المعطيات الصحيحة']},
      {key:'rule',label:'القانون/القاعدة',kind:'choice',answer:'rule1',options:[{value:'rule1',label:'ضع القاعدة الصحيحة'},{value:'rule2',label:'مشتت 1'},{value:'rule3',label:'مشتت 2'}]},
      {key:'substitution',label:'التعويض',kind:'text',answers:['ضع التعويض الصحيح']},
      {key:'operations',label:'العمليات الحسابية',kind:'text',answers:['ضع الخطوات/القيمة الصحيحة']},
      {key:'result',label:'الناتج النهائي',kind:'text',answers:['ضع الناتج الصحيح']}
    ];
  }
  common.model_solution='اكتب الحل الوزاري النموذجي هنا بعد تدقيقه من المصدر.';
  ta.value=JSON.stringify(common,null,2);
}

const typeSelect=document.getElementById('typeSelect');
const meaningWrap=document.getElementById('meaningWrap');
const correctWrap=document.getElementById('correctWrap');
const optionsWrap=document.getElementById('optionsWrap');
const matchWrap=document.getElementById('matchWrap');
const geneticsWrap=document.getElementById('geneticsWrap');
const calculationWrap=document.getElementById('calculationWrap');

function syncType(){
  const t=typeSelect.value;
  meaningWrap.classList.toggle('qm-hidden',t!=='meaning');
  optionsWrap.classList.toggle('qm-hidden',!(t==='mcq'||t==='meaning'));
  matchWrap.classList.toggle('qm-hidden',t!=='match');
  geneticsWrap.classList.toggle('qm-hidden',t!=='genetics');
  calculationWrap.classList.toggle('qm-hidden',t!=='calculation');
  correctWrap.classList.toggle('qm-hidden',t==='match'||t==='genetics'||t==='calculation');
}
typeSelect.addEventListener('change',syncType);syncType();
</script>
<script defer src="<?= BASE_URL ?>/assets/js/app.js?v=20260929-v586stories"></script>
</body>
</html>
