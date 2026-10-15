<?php
declare(strict_types=1);
$pdo=new PDO((string)getenv('RELEASE_TEST_DSN'),'root','synthetic-ci-only',[
    PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
if($pdo->query('SELECT DATABASE()')->fetchColumn()!=='murshid_release_test')throw new RuntimeException('Synthetic database required');
$fixture=json_decode((string)file_get_contents($argv[1]),true,512,JSON_THROW_ON_ERROR);
$pdo->exec('ALTER TABLE questions ADD question_version INT NOT NULL DEFAULT 1, ADD content_updated_at DATETIME NULL, ADD explanation TEXT NULL');
$insert=$pdo->prepare('INSERT INTO questions(id,chapter_id,question_text,correct_answer,question_version,explanation) VALUES(424242,1,?,?,3,?)');
$insert->execute(['الدور األول','P ≠ p؛ 10²','   ']);
function exact(bool $ok,string $message):void {if(!$ok)throw new RuntimeException($message);}
exact($pdo->exec($fixture['forward'])===1,'Fresh matching source not updated');
$row=$pdo->query('SELECT * FROM questions WHERE id=424242')->fetch();
exact($row['question_text']==='الدور الأول' && $row['correct_answer']==='P ≠ p؛ 10²' && $row['explanation']==='' && (int)$row['question_version']===4,'Text correction changed grading or empty-string semantics');
exact($pdo->exec($fixture['forward'])===0,'Repeated migration updated twice');
exact($pdo->exec($fixture['reverse'])===1,'Matching guarded rollback failed');
$row=$pdo->query('SELECT * FROM questions WHERE id=424242')->fetch();
exact($row['question_text']==='الدور األول' && (int)$row['question_version']===5,'Rollback must invalidate caches with a newer revision');
$pdo->exec("UPDATE questions SET question_version=3,question_text='تعديل أحدث' WHERE id=424242");
exact($pdo->exec($fixture['forward'])===0,'Newer source text overwritten');
$pdo->exec('DELETE FROM questions WHERE id=424242');
exact($pdo->exec($fixture['forward'])===0,'Deleted question recreated');
echo "PASS generated SQL on MySQL: exact text/version guards, repeat safety, rollback, empty strings and immutable answers\n";
