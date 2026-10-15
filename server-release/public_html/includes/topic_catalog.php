<?php
declare(strict_types=1);

/** Shared, read-only topic catalogue. A subject ID is never shared across grades. */
function topic_text_key(string $value): string {
    $value = strtr($value, ['أ'=>'ا','إ'=>'ا','آ'=>'ا','ى'=>'ي','ـ'=>'','٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9','۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9']);
    $value = preg_replace('/[\x{0610}-\x{061A}\x{064B}-\x{065F}\x{0670}\x{06D6}-\x{06ED}\x{200B}-\x{200F}\x{202A}-\x{202E}\x{2066}-\x{2069}\x{FEFF}]/u', '', $value) ?? $value;
    return mb_strtolower(trim(preg_replace('/[\s\x{00A0}]+/u', ' ', $value) ?? $value), 'UTF-8');
}

/** These are the existing banks' labels, not a claim that every syllabus is complete. */
function topic_curriculum(string $grade, string $subject): array {
    $common = [
        'التربية الإسلامية'=>['أحكام التلاوة','الحفظ','الفهم والشرح','المعاني والمفردات','الحديث الشريف','التهذيب والسلوك','القصص والعبر','الأحكام والعبادات'],
        'اللغة العربية'=>['القواعد','الأدب والنصوص','القراءة والمطالعة','الإملاء','التعبير والإنشاء'],
    ];
    if (topic_text_key($grade) === topic_text_key('الثالث المتوسط')) {
        $common += [
            'اللغة الإنكليزية'=>['Unit 1','Unit 2','Unit 3','Unit 4','Unit 5','Unit 6','Unit 7','Unit 8'],
            'الرياضيات'=>['العلاقات والمتباينات في الأعداد الحقيقية','المقادير الجبرية','المعادلات','الهندسة الإحداثية والنسب المثلثية','الهندسة والقياس','الإحصاء والاحتمالات'],
            'الاجتماعيات'=>['جغرافية وطننا العراق','تاريخنا وحضارتنا وهويتنا'],
            'الأحياء'=>['الجهاز العصبي','الجهاز الهيكلي','الجهاز العضلي','الجهاز الهضمي','جهاز الدوران','الجهاز التنفسي','الجهاز البولي والإخراج','الجهاز التناسلي','أعضاء الحس','الإفراز','المناعة'],
            'الكيمياء'=>['التركيب الذري للمادة','الزمرتان الأولى والثانية','الزمرة الثالثة','المحاليل والتعبير عن التركيز','الزمرة الرابعة','مدخل في الكيمياء العضوية','الزمرة الخامسة VA','الزمرة السادسة VIA','الزمرة السابعة'],
            'الفيزياء'=>['الكهربائية الساكنة','المغناطيسية','التيار الكهربائي','البطارية والقوة الدافعة الكهربائية','الطاقة والقدرة الكهربائية','الكهربائية والمغناطيسية','المحولة الكهربائية','تكنولوجيا مصادر الطاقة'],
        ];
    } elseif (topic_text_key($grade) === topic_text_key('السادس العلمي')) {
        $common += [
            'الرياضيات'=>['الأعداد المركبة','القطوع المخروطية','تطبيقات التفاضل','التكامل','المعادلات التفاضلية','الهندسة الفضائية'],
            'الأحياء'=>['الخلية','الأنسجة النباتية والحيوانية','التكاثر','التكوين الجنيني','علم الوراثة'],
            'الكيمياء'=>['الثرموداينمك','الاتزان الكيميائي','الاتزان الأيوني','الكيمياء الكهربائية','الكيمياء التناسقية','الكيمياء التحليلية','الكيمياء العضوية','الكيمياء الحياتية'],
            'الفيزياء'=>['المتسعات','الحث الكهرومغناطيسي','التيار المتناوب','الموجات الكهرومغناطيسية','البصريات الفيزيائية','الفيزياء الحديثة','إلكترونيات الحالة الصلبة','الأطياف الذرية والليزر','الفيزياء النووية'],
        ];
    } elseif (topic_text_key($grade) === topic_text_key('السادس الأدبي')) {
        $common += [
            'التاريخ'=>['البلاد العربية في العهد العثماني 1516-1918','الاستعمار الأوروبي للبلاد العربية','العراق في العهد العثماني','العراق أثناء وبعد الحرب العالمية الأولى','العراق 1921-1939','العراق من الحرب العالمية الثانية إلى 1958','حركات التحرر العربية'],
            'الرياضيات'=>['مبرهنة ذات الحدين','الغايات والاستمرارية','الاشتقاق','التكامل'],
            'الجغرافية'=>['جغرافية السكان','جغرافية المدن والريف','الجغرافية الزراعية','جغرافية الصناعة والتعدين','جغرافية النقل والمواصلات والتجارة','الجغرافية السياسية والسياحة'],
            'الاقتصاد'=>['علم الاقتصاد','العرض والطلب وسلوك المستهلك','الدخل والاستهلاك والادخار','المالية العامة والسياسة النقدية','التخلف والتنمية الاقتصادية','التجارة الخارجية والمنظمات الاقتصادية'],
            'النقد الأدبي'=>['النقد العربي القديم','المذاهب الأدبية','المناهج النقدية'],
        ];
    }
    if (in_array(topic_text_key($grade), [topic_text_key('السادس العلمي'),topic_text_key('السادس الأدبي')], true)) {
        $common['اللغة الإنكليزية']=['الإملاء Spelling','التوصيلات Match','الأدب Literature','وزاريات القراءة Reading','القواعد والوظائف Grammar','المفردات Vocabulary'];
    }
    foreach ($common as $name=>$topics) if (topic_text_key($name)===topic_text_key($subject)) return $topics;
    return [];
}

/**
 * الموضع المنهجي للموضوع داخل مادته.
 * لا نعتمد على sort_order القديم في قاعدة البيانات لأن بنوك v34 كانت تحمل
 * فهرساً عاماً عبر جميع الصفوف والمواد، وهذا قد يجعل الفصل الثامن يظهر قبل الأول.
 */
function topic_curriculum_position(string $grade,string $subject,string $topic,int $fallback=1000): int {
    $ordered=topic_curriculum($grade,$subject);
    $key=topic_text_key($topic);
    foreach($ordered as $i=>$name){
        if(topic_text_key((string)$name)===$key) return $i+1;
    }
    return max(1,$fallback);
}

function topic_display_name(string $subject, string $name, string $grade=''): string {
    $name = trim(preg_replace('/[\s\x{00A0}]+/u', ' ', $name) ?? $name);
    $name = preg_replace('/^القواعد\s*[—–-]\s*/u', '', $name) ?? $name;
    $ordinal='(?:الأول|الاول|الأولى|الاولي|الاولى|الثاني|الثانية|الثالث|الثالثة|الرابع|الرابعة|الخامس|الخامسة|السادس|السادسة|السابع|السابعة|الثامن|الثامنة|التاسع|التاسعة|العاشر|العاشرة|[0-9٠-٩۰-۹]+)';
    $name = preg_replace('/^(?:الفصل|الوحدة)\s+'.$ordinal.'\s*[—–:\-]\s*/u','',$name) ?? $name;
    $key = topic_text_key($name);
    if (in_array(topic_text_key($subject),[topic_text_key('اللغة الإنكليزية')],true) && preg_match('/unit[eé]?\s*0?([1-9])/u',$key,$m)) {
        return 'Unit '.$m[1];
    }
    $names = topic_curriculum($grade,$subject);
    // Convert only grade-scoped generic numbers for which this catalogue has an
    // explicit ordered curriculum. This prevents names from another grade from
    // leaking into third-intermediate while still repairing old "الفصل الأول" rows.
    $gradeKey=topic_text_key($grade);$subjectKey=topic_text_key($subject);
    $canMapGeneric=(
        $gradeKey===topic_text_key('السادس العلمي') && in_array($subjectKey,array_map('topic_text_key',['الرياضيات','الأحياء','الكيمياء','الفيزياء']),true)
    ) || (
        $gradeKey===topic_text_key('الثالث المتوسط') && in_array($subjectKey,array_map('topic_text_key',['الرياضيات','الأحياء','الكيمياء','الفيزياء','الاجتماعيات']),true)
    );
    if ($canMapGeneric) {
        $ord=['الاول'=>1,'الاولي'=>1,'الثاني'=>2,'الثانية'=>2,'الثالث'=>3,'الثالثة'=>3,'الرابع'=>4,'الرابعة'=>4,'الخامس'=>5,'الخامسة'=>5,'السادس'=>6,'السادسة'=>6,'السابع'=>7,'السابعة'=>7,'الثامن'=>8,'الثامنة'=>8,'التاسع'=>9,'التاسعة'=>9];
        if (preg_match('/^(?:الفصل|الوحدة)\s+(\S+)$/u',$key,$m)) {
            $n=$ord[$m[1]]??(ctype_digit($m[1])?(int)$m[1]:0);
            if ($n>0 && isset($names[$n-1])) return $names[$n-1];
        }
    }
    foreach ($names as $canonical) if (topic_text_key($canonical)===$key) return $canonical;
    return $name;
}

function topic_theme(string $subject): string {
    $key=topic_text_key($subject);
    foreach (['فيزياء'=>'physics','كيمي'=>'chemistry','احياء'=>'biology','رياض'=>'math','اسلام'=>'islamic','عرب'=>'arabic','ادب'=>'arabic','انك'=>'english','اجتماع'=>'social','تاريخ'=>'social','جغراف'=>'social','اقتصاد'=>'economy'] as $needle=>$theme) {
        if (mb_strpos($key,$needle)!==false) return $theme;
    }
    return 'default';
}

function topic_icon_name(string $subject, string $topic): string {
    $key=topic_text_key($topic);
    foreach (['معاني'=>'language','مفردات'=>'language','قواعد'=>'language','املاء'=>'edit','spelling'=>'edit','match'=>'checklist','توصيل'=>'checklist','حفظ'=>'book','تلاوة'=>'book','عصبي'=>'brain','وراث'=>'dna','خلية'=>'dna','هندس'=>'calculator','معادل'=>'calculator','جبر'=>'calculator','تاريخ'=>'landmark','جغرافي'=>'map','مغناطيس'=>'atom','كهرب'=>'activity'] as $needle=>$icon) {
        if (mb_strpos($key,$needle)!==false) return $icon;
    }
    return ['physics'=>'atom','chemistry'=>'flask','biology'=>'dna','math'=>'calculator','islamic'=>'mosque','arabic'=>'book','english'=>'globe','social'=>'map','economy'=>'economy'][topic_theme($subject)]??'book';
}

function topic_live_sql(string $alias='q'): string {
    if (!preg_match('/^[a-zA-Z_][a-zA-Z0-9_]*$/',$alias)) throw new InvalidArgumentException('Invalid SQL alias');
    // حماية دائمة: لا تسمح لنصوص التحقق الداخلية/الميتا بالظهور حتى لو تعذر تشغيل تنظيف قاعدة البيانات.
    return "$alias.status=1"
        ." AND ($alias.moderation_status='approved' OR $alias.moderation_status IS NULL)"
        ." AND TRIM($alias.question_text)<>'' AND TRIM($alias.correct_answer)<>''"
        ." AND $alias.question_text NOT LIKE 'تحقق بصري%'"
        ." AND LOWER($alias.question_text) NOT LIKE '%v25%تحقق بصري%'"
        ." AND $alias.question_text NOT LIKE '%هل الإجابة «%» صحيحة%'"
        ." AND $alias.question_text NOT LIKE 'الإجابة عن «%» هي «%'"
        ." AND $alias.question_text NOT LIKE 'صح أم خطأ: الإجابة عن «%» هي «%'"
        ." AND $alias.question_text NOT LIKE '%تدريب تحقق وزاري:%'"
        ." AND $alias.question_text NOT LIKE '«%» هي «%'"
        ." AND $alias.question_text NOT LIKE '%الإجابة المناسبة للسؤال%'"
        ." AND $alias.question_text NOT LIKE '%عند الإجابة عن «%'"
        ." AND $alias.question_text NOT LIKE '%راجع المعلومة: يرتبط السؤال%'"
        ." AND $alias.question_text NOT LIKE '%تدريب تثبيت: الجواب%'"
        ." AND $alias.question_text NOT LIKE '%وفق المفهوم الدراسي، هل إجابة%'"
        ." AND $alias.question_text NOT LIKE '%قيّم العبارة: إذا سُئلت%'"
        ." AND $alias.question_text NOT LIKE '%اختر صح أو خطأ للرابطة%'"
        ." AND $alias.question_text NOT LIKE '%تأكد من المراجعة: يقابل السؤال%'";
}

/** Pure grouping is shared by queries and tests. No question is discarded by deduplication. */
function topic_group_rows(array $rows, bool $onlyReady=true): array {
    $groups=[];
    foreach ($rows as $row) {
        $name=topic_display_name((string)$row['subject_name'],(string)$row['name'],(string)($row['grade_name']??''));
        $key=(int)$row['subject_id'].'|'.topic_text_key($name);
        $id=(int)$row['id'];
        if (!isset($groups[$key])) {
            $groups[$key]=$row;
            $groups[$key]['id']=$id;
            $groups[$key]['name']=$name;
            $groups[$key]['display_name']=$name;
            $groups[$key]['chapter_ids']=[];
            $groups[$key]['questions_count']=0;
            $groups[$key]['theme']=topic_theme((string)$row['subject_name']);
            $groups[$key]['icon']=topic_icon_name((string)$row['subject_name'],$name);
            $groups[$key]['curriculum_order']=topic_curriculum_position(
                (string)($row['grade_name']??''),
                (string)$row['subject_name'],
                $name,
                1000+(int)($row['sort_order']??0)
            );
        }
        $groups[$key]['id']=min($groups[$key]['id'],$id);
        $groups[$key]['chapter_ids'][]=$id;
        $groups[$key]['questions_count']+=(int)($row['questions_count']??0);
        $groups[$key]['sort_order']=min((int)$groups[$key]['sort_order'],(int)$row['sort_order']);
    }
    $out=[];
    foreach ($groups as $g) {
        if ($onlyReady && (int)$g['questions_count']<1) continue;
        $g['chapter_ids']=array_values(array_unique($g['chapter_ids'])); sort($g['chapter_ids']);
        $out[]=$g;
    }
    // ترتيب الموضوعات بمنهج المادة: الفصل الأول ثم الثاني ثم الثالث...
    // وعند وجود موضوع قديم غير معروف نضعه بعد الموضوعات المنهجية بدلاً من إدخاله بينها.
    usort($out,static fn(array $a,array $b): int =>
        ((int)($a['subject_sort']??0)<=>(int)($b['subject_sort']??0))
        ?: ((int)$a['subject_id']<=>(int)$b['subject_id'])
        ?: ((int)($a['curriculum_order']??100000)<=>(int)($b['curriculum_order']??100000))
        ?: ((int)$a['sort_order']<=>(int)$b['sort_order'])
        ?: ((int)$a['id']<=>(int)$b['id'])
    );
    return $out;
}

function topic_catalog(PDO $pdo, int $gradeId, int $subjectId=0, bool $onlyReady=true): array {
    $live=topic_live_sql('q');
    $sql="SELECT c.id,c.subject_id,c.name,c.sort_order,c.status,s.name subject_name,s.sort_order subject_sort,g.name grade_name,COUNT(q.id) questions_count
        FROM chapters c JOIN subjects s ON s.id=c.subject_id JOIN grades g ON g.id=s.grade_id
        LEFT JOIN questions q ON q.chapter_id=c.id AND $live
        WHERE s.grade_id=? AND s.status=1 AND c.status=1 AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%'";
    $args=[$gradeId];
    if ($subjectId>0) {$sql.=' AND s.id=?';$args[]=$subjectId;}
    $sql.=' GROUP BY c.id,c.subject_id,c.name,c.sort_order,c.status,s.name,s.sort_order,g.name ORDER BY s.sort_order,s.id,c.sort_order,c.id';
    $q=$pdo->prepare($sql);$q->execute($args);
    return topic_group_rows($q->fetchAll(PDO::FETCH_ASSOC),$onlyReady);
}

function topic_table_exists(PDO $pdo, string $table): bool {
    $q=$pdo->prepare('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?');$q->execute([$table]);
    return (bool)$q->fetchColumn();
}

function topic_resolve_id(PDO $pdo, int $id): int {
    static $hasTable=null;
    if ($hasTable===null) $hasTable=topic_table_exists($pdo,'topic_merge_aliases');
    if (!$hasTable) return $id;
    $seen=[];
    for ($i=0;$i<20;$i++) {
        if (isset($seen[$id])) throw new RuntimeException('Topic alias cycle');
        $seen[$id]=true;
        $q=$pdo->prepare('SELECT target_id FROM topic_merge_aliases WHERE source_id=?');$q->execute([$id]);$next=(int)$q->fetchColumn();
        if (!$next) return $id;
        $id=$next;
    }
    throw new RuntimeException('Topic alias depth exceeded');
}

function topic_for_student(PDO $pdo, int $gradeId, int $chapterId): ?array {
    $chapterId=topic_resolve_id($pdo,$chapterId);
    $q=$pdo->prepare("SELECT s.id FROM chapters c JOIN subjects s ON s.id=c.subject_id WHERE c.id=? AND s.grade_id=? AND c.status=1 AND s.status=1 AND s.name NOT LIKE '%فرن%' AND LOWER(s.name) NOT LIKE '%french%'");
    $q->execute([$chapterId,$gradeId]);$subjectId=(int)$q->fetchColumn();
    if (!$subjectId) return null;
    // Direct links must obey the same rule as dashboards and APIs: a topic
    // without a runnable question is not a student destination.
    foreach (topic_catalog($pdo,$gradeId,$subjectId,true) as $topic) if (in_array($chapterId,$topic['chapter_ids'],true)) return $topic;
    return null;
}

function topic_id_list(array $topic): string {
    $ids=array_values(array_filter(array_map('intval',$topic['chapter_ids']??[]),static fn(int $id): bool=>$id>0));
    return $ids?implode(',',$ids):'0';
}

function topic_student_metrics(PDO $pdo, int $userId, int $gradeId, bool $onlyReady=true): array {
    $topics=topic_catalog($pdo,$gradeId,0,$onlyReady);$live=topic_live_sql('q');
    $q=$pdo->prepare("SELECT q.chapter_id,COUNT(DISTINCT ae.question_id) distinct_answered,COUNT(ae.id) attempts,SUM(ae.is_correct) correct,COUNT(DISTINCT CASE WHEN ae.is_correct=0 THEN ae.question_id END) wrong_distinct,MAX(ae.created_at) updated_at
        FROM answer_events ae JOIN questions q ON q.id=ae.question_id JOIN chapters c ON c.id=q.chapter_id JOIN subjects s ON s.id=c.subject_id
        WHERE ae.user_id=? AND s.grade_id=? AND s.status=1 AND c.status=1 AND $live GROUP BY q.chapter_id");
    $q->execute([$userId,$gradeId]);$by=[];foreach($q->fetchAll(PDO::FETCH_ASSOC) as $r)$by[(int)$r['chapter_id']]=$r;
    foreach($topics as &$t){
        foreach(['distinct_answered','attempts','correct','wrong_distinct'] as $k)$t[$k]=0;
        $t['updated_at']=null;
        foreach($t['chapter_ids'] as $id){$r=$by[$id]??[];foreach(['distinct_answered','attempts','correct','wrong_distinct'] as $k)$t[$k]+=(int)($r[$k]??0);if(strcmp((string)($r['updated_at']??''),(string)$t['updated_at'])>0)$t['updated_at']=$r['updated_at'];}
        $t['accuracy']=$t['attempts']?(int)round($t['correct']/$t['attempts']*100):0;
        $t['coverage']=min(100,(int)round($t['distinct_answered']/max(1,$t['questions_count'])*100));
        $t['progress_pct']=$t['coverage'];$t['current_index']=$t['distinct_answered'];
        $t['completed']=$t['questions_count']>0 && $t['distinct_answered']>=$t['questions_count'];
    }unset($t);return $topics;
}

/** Serialized on the parent row, including when invoked inside an import transaction. */
function topic_ensure(PDO $pdo, int $subjectId, string $name, int $sort=100): int {
    $own=!$pdo->inTransaction();
    if ($own) $pdo->beginTransaction();
    try {
        $q=$pdo->prepare('SELECT s.id,s.name,g.name grade_name FROM subjects s JOIN grades g ON g.id=s.grade_id WHERE s.id=? FOR UPDATE');$q->execute([$subjectId]);$s=$q->fetch(PDO::FETCH_ASSOC);
        if (!$s) throw new InvalidArgumentException('المادة غير موجودة.');
        $name=topic_display_name((string)$s['name'],$name,(string)$s['grade_name']);
        if ($name==='' || mb_strlen($name)>190) throw new InvalidArgumentException('اسم الموضوع مطلوب وبحد أقصى 190 حرفاً.');
        $q=$pdo->prepare('SELECT id,name,status FROM chapters WHERE subject_id=? ORDER BY status DESC,id FOR UPDATE');$q->execute([$subjectId]);$target=0;$mappedFallback=0;
        foreach ($q->fetchAll(PDO::FETCH_ASSOC) as $row) {
            // Prefer the physically canonical row. A legacy "الوحدة الثانية"
            // may display as "المقادير الجبرية" too; selecting it first and
            // renaming it would collide with the already canonical row.
            if (topic_text_key((string)$row['name'])===topic_text_key($name)) {$target=topic_resolve_id($pdo,(int)$row['id']);break;}
            if (!$mappedFallback && topic_text_key(topic_display_name((string)$s['name'],(string)$row['name'],(string)$s['grade_name']))===topic_text_key($name)) $mappedFallback=topic_resolve_id($pdo,(int)$row['id']);
        }
        if(!$target)$target=$mappedFallback;
        if (!$target) {
            // Atomic upsert prevents duplicate-key failures when setup is
            // resumed, double-clicked, or the database collation considers two
            // Arabic spellings equal although PHP did not.
            $q=$pdo->prepare('INSERT INTO chapters(subject_id,name,sort_order,status) VALUES(?,?,?,1)
                ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),sort_order=VALUES(sort_order),status=1');
            $q->execute([$subjectId,$name,max(0,$sort)]);$target=(int)$pdo->lastInsertId();
            if(!$target){
                $q=$pdo->prepare('SELECT id FROM chapters WHERE subject_id=? AND name=? ORDER BY id LIMIT 1');
                $q->execute([$subjectId,$name]);$target=(int)$q->fetchColumn();
            }
            if(!$target) throw new RuntimeException('تعذر إنشاء أو استعادة الموضوع.');
        } else {
            // An archived empty placeholder becomes usable again as soon as a
            // verified importer supplies content for that canonical topic.
            try{
                $pdo->prepare('UPDATE chapters SET name=?,status=1 WHERE id=?')->execute([$name,$target]);
            }catch(PDOException $e){
                if((string)$e->getCode()!=='23000')throw $e;
                $q=$pdo->prepare('SELECT id FROM chapters WHERE subject_id=? AND name=? ORDER BY id LIMIT 1');
                $q->execute([$subjectId,$name]);$canonical=(int)$q->fetchColumn();
                if(!$canonical)throw $e;
                $target=topic_resolve_id($pdo,$canonical);
                $pdo->prepare('UPDATE chapters SET status=1 WHERE id=?')->execute([$target]);
            }
        }
        if ($own) $pdo->commit();
        return $target;
    } catch (Throwable $e) {if($own && $pdo->inTransaction())$pdo->rollBack();throw $e;}
}
